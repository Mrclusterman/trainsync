"""Planes de salud sin fecha: ponerse en forma, fuerza y músculo, perder grasa (docs/proyecto/DISENO.md).

Determinista: mismas entradas, mismo plan. Cada regla cita su apartado del diseño (§n), que a su vez cita su fuente en
docs/proyecto/ESTADO_DEL_ARTE.md. Las sesiones de resistencia salen del catálogo del motor (training_engine.build_session).
"""
from dataclasses import dataclass, field
from datetime import date, timedelta
from itertools import combinations
from math import ceil

from app.services import training_engine as engine
from app.services.training_engine import WEEKDAYS

GOALS = ("fitness", "strength", "fat_loss")
LEVELS = ("beginner", "intermediate", "advanced")
CARDIO = ("walking", "running", "cycling", "swimming_pool")


@dataclass
class HealthProfile:
    goal: str  # fitness / strength / fat_loss
    days: dict[str, int]  # día disponible (MON..SUN) -> minutos
    strength: str = "gym"  # gym / bodyweight / none
    cardio: list[str] = field(default_factory=lambda: ["walking"])
    level: str = "beginner"
    age: int | None = None
    parq_restricted: bool = False  # PAR-Q+ con alguna alerta (§9)
    lthr: float | None = None
    max_hr: float | None = None
    css: float | None = None

    def __post_init__(self):
        assert self.goal in GOALS and self.level in LEVELS and self.strength in ("gym", "bodyweight", "none")
        assert set(self.cardio) <= set(CARDIO) and set(self.days) <= set(WEEKDAYS)


# ---------------------------------------------------------------- §3 dosis semanal

# Meta del bloque estable. aerobic = minutos moderados equivalentes (1 vigoroso = 2 moderados, OMS);
# sets = series por patrón de movimiento y semana (≈ grupo muscular principal).
TARGET = {
    "fitness": {"aerobic": 300, "strength_days": (2, 3), "sets": 10},  # OMS 150–300; ACSM 2026 ~10 series
    "strength": {"aerobic": 150, "strength_days": (3, 4), "sets": 20},  # Schoenfeld 2017: más series, más músculo
    "fat_loss": {"aerobic": 250, "strength_days": (2, 3), "sets": 10},  # Donnelly 2009: > 250 min
}
FLOOR_AEROBIC, FLOOR_STRENGTH_DAYS = 150, 2  # suelo de la OMS para todos (§1)
START = {"beginner": 0.6, "intermediate": 0.8, "advanced": 1.0}  # [Inferencia] §3
START_SETS_PER_SESSION = {"beginner": 1, "intermediate": 2, "advanced": 3}  # [Inferencia] §3
AEROBIC_STEP = 1.10  # +10 % por semana de carga [Inferencia] §4
DELOAD = 0.7  # semana 4 del bloque: −30 % [Práctica: MOTOR §8]


def strength_days(p: HealthProfile) -> int:
    if p.strength == "none":
        return 0
    lo, hi = TARGET[p.goal]["strength_days"]
    n = lo if p.level == "beginner" else hi
    if p.age is not None and p.age >= 65:
        n = max(n, 3)  # OMS: equilibrio y fuerza 3 días o más en mayores de 65
    return min(n, len(training_days(p)))


def next_base(base: float, peak: float, adherence: float) -> float:
    """Revisión del bloque por adherencia (§4): ≥ 85 % sube, 60–85 % repite, < 60 % baja. [Inferencia]"""
    if adherence >= 0.85:
        return peak * 1.05
    if adherence >= 0.60:
        return base
    return base * 0.8


def week_dose(p: HealthProfile, adherence: list[float], week_in_block: int) -> dict:
    """Dosis de una semana: minutos aeróbicos equivalentes, días de fuerza y series por patrón.

    adherence: una fracción (sesiones hechas / planificadas) por cada bloque ya terminado.
    """
    target = TARGET[p.goal]
    n_strength = strength_days(p)
    aero = max(60.0, target["aerobic"] * START[p.level])
    sets = float(n_strength * START_SETS_PER_SESSION[p.level])
    for done in adherence:
        aero = min(next_base(aero, min(aero * AEROBIC_STEP ** 2, target["aerobic"]), done), target["aerobic"])
        sets = min(next_base(sets, min(sets + 2, target["sets"]), done), target["sets"])
    week = min(week_in_block, 2)
    aero = min(aero * AEROBIC_STEP ** week, target["aerobic"])
    sets = min(sets + week, target["sets"])  # +1 serie por patrón y semana de carga
    if week_in_block == 3:
        aero, sets = aero * DELOAD, sets * DELOAD
    return {"aerobic": round(aero), "strength_days": n_strength, "sets": max(round(sets), n_strength)}


# ---------------------------------------------------------------- §5 reparto semanal

def training_days(p: HealthProfile) -> list[str]:
    """Días disponibles en orden de la semana, dejando al menos uno de descanso (§5.1)."""
    days = [d for d in WEEKDAYS if p.days.get(d)]
    if len(days) == 7:  # sin descanso: se quita el día con menos minutos (el último si empatan)
        days.remove(min(reversed(days), key=lambda d: p.days[d]))
    return days


def spread(days: list[str], n: int) -> list[str]:
    """Los n días más separados entre sí (máxima separación mínima, también entre semanas) (§5.2)."""
    if n == 0:
        return []
    idx = {d: WEEKDAYS.index(d) for d in days}

    def min_gap(combo):
        ix = sorted(idx[d] for d in combo)
        gaps = [b - a for a, b in zip(ix, ix[1:])] + [ix[0] + 7 - ix[-1]]
        return min(gaps)

    return list(max(combinations(days, n), key=lambda c: (min_gap(c), [-idx[d] for d in c])))


# ---------------------------------------------------------------- §6 sesión de fuerza

PATTERNS = {  # patrón -> (gimnasio, peso corporal)
    "sentadilla": ("Sentadilla goblet o con barra", "Sentadilla (a una pierna al progresar)"),
    "bisagra": ("Peso muerto rumano", "Puente de glúteo (a una pierna al progresar)"),
    "zancada": ("Zancada con mancuernas", "Zancada atrás"),
    "empuje horizontal": ("Press de banca con mancuernas", "Flexiones (inclinadas al empezar)"),
    "empuje vertical": ("Press militar con mancuernas", "Flexiones en pica"),
    "tracción horizontal": ("Remo con mancuerna", "Remo invertido bajo una mesa firme"),
    "tracción vertical": ("Jalón al pecho", "Dominadas asistidas con goma"),
    "core": ("Plancha frontal y lateral", "Plancha frontal y lateral"),
}
FULL_BODY = ("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal", "core")
UPPER = ("empuje horizontal", "tracción horizontal", "empuje vertical", "tracción vertical", "core")
LOWER = ("sentadilla", "bisagra", "zancada", "core")
ACCESSORY = ("core", "zancada", "empuje vertical", "tracción vertical")  # lo primero que sale si falta tiempo
BALANCE = "Equilibrio: apoyo a una pierna 3 × 30″ por lado y marcha en tándem 3 × 10 pasos"
# Repeticiones y RIR por objetivo y nivel (§6) [ACSM 2009/2026; rangos: Inferencia]
REPS = {"beginner": ("10–15", 3), "fitness": ("8–12", 2), "fat_loss": ("8–12", 2), "strength": ("6–12", 1)}
MIN_PER_SET = 2.5  # serie + descanso [Inferencia]


def strength_session(p: HealthProfile, patterns: tuple, sets_per_exercise: int, extra_rir: int = 0) -> dict:
    reps, rir = REPS["beginner" if p.level == "beginner" else p.goal]
    rir += extra_rir
    if p.parq_restricted:
        rir = max(rir, 3)  # PAR-Q+ con alerta: lejos del fallo (§9)
    kit = 1 if p.strength == "bodyweight" else 0
    # El core es isométrico: se prescribe por tiempo, no por repeticiones ni RIR [Práctica]
    exercises = [f"{PATTERNS[pt][kit]}: {sets_per_exercise} × 20–45″ por lado" if pt == "core"
                 else f"{PATTERNS[pt][kit]}: {sets_per_exercise} × {reps}, RIR {rir}" for pt in patterns]
    if p.age is not None and p.age >= 65:
        exercises.append(BALANCE)
    minutes = round(10 + len(patterns) * sets_per_exercise * MIN_PER_SET)  # 10′ de calentamiento
    return {"sport": "strength", "type": "strength", "name": "Fuerza", "patterns": list(patterns),
            "sets_per_exercise": sets_per_exercise, "exercises": exercises, "rir": rir, "duration_min": minutes,
            "tss": round(engine.strength_tss(10 - rir, minutes)),
            "notes": "Progresión doble: cuando completes el tope de repeticiones con ese RIR en todas las series, "
                     "sube la carga un 2–10 % y vuelve al mínimo de repeticiones."}


def strength_week(p: HealthProfile, days: list[str], sets: int) -> dict[str, dict]:
    """Cuerpo completo con ≤ 3 días; torso/pierna alternos con 4 (§5.2)."""
    if not days:
        return {}
    plan = [FULL_BODY] * len(days) if len(days) <= 3 else [UPPER, LOWER] * (len(days) // 2)
    times = {pt: sum(pt in s for s in plan) for pt in PATTERNS}
    out = {}
    for day, patterns in zip(days, plan):
        per_ex = max(ceil(sets / max(times[pt] for pt in patterns if pt != "core")), 1)
        session = strength_session(p, patterns, per_ex)
        # Tiene que caber en los minutos del día (§5.5): primero menos series, luego fuera los accesorios
        while session["duration_min"] > p.days[day] and (per_ex > 1 or any(a in patterns for a in ACCESSORY)):
            if per_ex > 1:
                per_ex -= 1
            else:
                patterns = tuple(pt for pt in patterns if pt != next(x for x in ACCESSORY if x in patterns))
            session = strength_session(p, patterns, per_ex)
        out[day] = session
    return out


# ---------------------------------------------------------------- §7 sesión de resistencia

# Minutos moderados equivalentes por zona: Z1 es actividad ligera y no cuenta para la OMS; Z4+ es vigorosa (×2)
EQUIV = {"Z1": 0, "Z2": 1, "Z3": 1, "Z4": 2, "Z5": 2, "Z6": 2}


def equivalent_minutes(session: dict) -> float:
    total = 0.0
    for b in session["blocks"]:
        total += b["reps"] * b["minutes"] * EQUIV[b["zone"]]
        total += b["reps"] * b.get("rest_minutes", 0) * EQUIV[b.get("rest_zone", "Z1")]
    return total


def cardio_session(p: HealthProfile, sport: str, kind: str, equivalent: float, week: int, cap: int) -> dict | None:
    """Sesión del catálogo cuyo tiempo moderado equivalente se acerca a `equivalent`, sin pasar de `cap` minutos."""
    ep = engine.Profile(active_sports=[sport], lthr=p.lthr, max_hr=p.max_hr, css=p.css)
    entry = engine.pick_session(kind, sport, "base", week)
    if entry is None:
        return None
    mid = sum(entry["duration"]) / 2
    total = min(max(equivalent, 20), cap)
    session = None
    for _ in range(5):  # la relación duración/equivalentes no es lineal (calentamientos en Z1): se itera
        candidate = engine.build_session(kind, sport, ep, total / mid, "base", week)
        if candidate["duration_min"] > cap:
            break
        session, got = candidate, equivalent_minutes(candidate)
        if got <= 0 or abs(got - equivalent) <= 0.1 * equivalent or total >= cap:
            break
        total = min(total * equivalent / got, cap)
    if session is None:
        return None
    session["equivalent_min"] = round(equivalent_minutes(session))
    return session


def cardio_kinds(p: HealthProfile, n: int) -> list[str]:
    """Intensidad de las sesiones de la semana: moderada para principiantes y PAR-Q+ con alerta; si no, polarizada
    (una de calidad cada 3 sesiones, como mucho dos). [MOTOR §1; Inferencia en el reparto]"""
    if p.level == "beginner" or p.parq_restricted or n < 3:
        return ["aerobic"] * n
    quality = "vo2max" if p.goal == "fat_loss" else "threshold"
    hard = min(n // 3, 2)
    return [quality if i < hard else "aerobic" for i in range(n)]


# ---------------------------------------------------------------- semana completa

def week_plan(p: HealthProfile, start: date, adherence: list[float] | None = None, week_in_block: int = 0) -> dict:
    """Plan de 7 días a partir de `start` (lunes). Devuelve la dosis, los días y los avisos."""
    adherence = adherence or []
    dose = week_dose(p, adherence, week_in_block)
    days = training_days(p)
    s_days = spread(days, dose["strength_days"])
    strength = strength_week(p, s_days, dose["sets"])
    warnings = []
    if p.strength == "none":
        warnings.append("Sin fuerza no se cumple la recomendación de la OMS (2 días o más por semana).")
    if any(s["duration_min"] > p.days[d] for d, s in strength.items()):
        warnings.append("Hay días con menos tiempo del que pide la sesión mínima de fuerza (3 ejercicios, 1 serie).")
    planned_sets = {pt: sum(s["sets_per_exercise"] for s in strength.values() if pt in s["patterns"])
                    for pt in ("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal")}
    if strength and min(planned_sets.values()) < dose["sets"]:
        warnings.append(f"Con el tiempo disponible haces {min(planned_sets.values())} de {dose['sets']} series "
                        "semanales por grupo muscular.")

    # Resistencia: primero los días sin fuerza; si no llegan, también los de fuerza (sesión combinada)
    free = [d for d in days if d not in s_days]
    slots = free + [d for d in s_days if p.days[d] - strength[d]["duration_min"] >= 20]
    sports = p.cardio or ["walking"]
    pending, plan_days, done_eq = float(dose["aerobic"]), {}, 0.0
    caps = {d: p.days[d] - (strength[d]["duration_min"] if d in strength else 0) for d in slots}
    kinds = cardio_kinds(p, len(slots))
    for i, day in enumerate(slots):
        if pending <= 5:
            break
        cap = caps[day]
        # Reparto proporcional a los minutos libres de cada día que queda (§5.5)
        share = pending * cap / sum(caps[d] for d in slots[i:])
        sport = sports[i % len(sports)]
        # En fuerza y músculo, nada de carrera el día de pierna si hay otra opción (Schumann/Lundberg 2022, §5.4)
        if day in strength and "sentadilla" in strength[day]["patterns"] and sport == "running":
            sport = next((s for s in sports if s != "running"), sport)
            if sport == "running" and p.goal == "strength":
                kinds[i] = "aerobic"
        session = cardio_session(p, sport, kinds[i], share, WEEKDAYS.index(day) + start.isocalendar().week, cap)
        if session is None and kinds[i] != "aerobic":
            session = cardio_session(p, sport, "aerobic", share, start.isocalendar().week, cap)
        if session:
            plan_days[day] = session
            pending -= session["equivalent_min"]
            done_eq += session["equivalent_min"]
    if done_eq + 5 < dose["aerobic"]:
        warnings.append(f"Con los días y minutos disponibles llegas a {done_eq:.0f} de {dose['aerobic']} min "
                        "aeróbicos equivalentes esta semana.")

    out = []
    for d in range(7):
        day = start + timedelta(d)
        wd = WEEKDAYS[day.weekday()]
        sessions = [s for s in (strength.get(wd), plan_days.get(wd)) if s]
        if p.goal == "strength" and len(sessions) == 2:
            pass  # fuerza primero: es el objetivo (§5.4)
        elif len(sessions) == 2:
            sessions.reverse()  # forma y pérdida de grasa: la sesión aeróbica primero (§5.4)
        out.append({"date": day.isoformat(), "weekday": wd, "sessions": sessions})
    return {"goal": p.goal, "week_in_block": week_in_block, "dose": dose, "days": out, "warnings": warnings,
            "equivalent_min": round(done_eq)}


# ---------------------------------------------------------------- §8 ajuste diario sin reloj

def readiness(sleep: int, energy: int, soreness: int) -> str:
    """Tres preguntas de 1 (mal) a 5 (bien). Dos o más en 1–2 → suave; todas en 1 → descanso activo. [Inferencia]"""
    answers = (sleep, energy, soreness)
    assert all(1 <= a <= 5 for a in answers)
    if all(a == 1 for a in answers):
        return "rest"
    return "easy" if sum(a <= 2 for a in answers) >= 2 else "normal"


def adjust_day(p: HealthProfile, sessions: list[dict], status: str, week: int) -> tuple[list[dict], str | None]:
    """Aplica el estado del día: suave = resistencia moderada y más corta, fuerza con una serie menos y RIR + 1."""
    if status == "normal" or not sessions:
        return sessions, None
    if status == "rest":
        walk = cardio_session(p, "walking", "recovery", 0, week, 30)
        return ([walk] if walk else []), "Hoy descanso activo: un paseo suave y nada más."
    out = []
    for s in sessions:
        if s["sport"] == "strength":
            out.append(strength_session(p, tuple(s["patterns"]), max(s["sets_per_exercise"] - 1, 1), extra_rir=1))
        else:
            easier = cardio_session(p, s["sport"], "aerobic", s["equivalent_min"] * 0.7, week, s["duration_min"])
            out.append(easier or s)
    return out, "Hoy en versión suave: has dormido o te notas peor de lo normal."
