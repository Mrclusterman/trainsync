"""Planes de salud sin fecha: ponerse en forma, fuerza y músculo, perder grasa (docs/proyecto/DISENO.md).

Determinista: mismas entradas, mismo plan. Cada regla cita su apartado del diseño (§n), que a su vez cita su fuente en
docs/proyecto/ESTADO_DEL_ARTE.md. Las sesiones de resistencia salen del catálogo del motor (training_engine.build_session).
"""
from dataclasses import dataclass, field, replace
from datetime import date, timedelta
from itertools import combinations
from math import ceil, floor

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
    intensity_cap: str = "vigorous"  # tope del cribado del ACSM (screening.py): light_moderate / moderate / vigorous
    lthr: float | None = None
    max_hr: float | None = None
    css: float | None = None

    def __post_init__(self):
        assert self.goal in GOALS and self.level in LEVELS and self.strength in ("gym", "bodyweight", "none")
        assert set(self.cardio) <= set(CARDIO) and set(self.days) <= set(WEEKDAYS)
        assert self.intensity_cap in ("light_moderate", "moderate", "vigorous")


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
        # Nunca por debajo de 60 min: algo de actividad es mejor que nada (OMS 2020) [Inferencia en la cifra]
        aero = max(min(next_base(aero, min(aero * AEROBIC_STEP ** 2, target["aerobic"]), done), target["aerobic"]), 60)
        sets = min(next_base(sets, min(sets + 2, target["sets"]), done), target["sets"])
    week = min(week_in_block, 2)
    minutes = round(aero)
    for _ in range(week):  # sobre el valor ya redondeado y hacia abajo: la subida que se ve nunca pasa del 10 %
        minutes = min(floor(minutes * AEROBIC_STEP), target["aerobic"])
    sets = min(sets + week, target["sets"])  # +1 serie por patrón y semana de carga
    if week_in_block == 3:
        minutes, sets = round(minutes * DELOAD), sets * DELOAD
    return {"aerobic": minutes, "strength_days": n_strength, "sets": max(round(sets), n_strength)}


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
    # Tope del cribado (§9): lejos del fallo cuando no se permite intensidad vigorosa [Inferencia: RIR ≈ intensidad]
    rir = max(rir, {"light_moderate": 3, "moderate": 2, "vigorous": 0}[p.intensity_cap])
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


def strength_week(p: HealthProfile, days: list[str], sets: int, budget: dict[str, int] | None = None) -> dict[str, dict]:
    """Cuerpo completo con ≤ 3 días; torso/pierna alternos con 4 (§5.2). budget: minutos máximos por día."""
    if not days:
        return {}
    plan = [FULL_BODY] * len(days) if len(days) <= 3 else [UPPER, LOWER] * (len(days) // 2)
    times = {pt: sum(pt in s for s in plan) for pt in PATTERNS}
    out = {}
    # Ondulación diaria: con 3 días de cuerpo completo, pesado / ligero / medio con las mismas series en total, para
    # variar la carga diaria (monotonía de Foster). [Práctica; ACSM 2026: la periodización no cambia el resultado medio]
    wave = (1, -1, 0) if len(days) == 3 else (0,) * len(days)
    for (day, patterns), delta in zip(zip(days, plan), wave):
        per_ex = max(ceil(sets / max(times[pt] for pt in patterns if pt != "core")) + delta, 1)
        session = strength_session(p, patterns, per_ex)
        # Tiene que caber en los minutos del día (§5.5): primero menos series, luego fuera los accesorios
        limit = budget[day] if budget else p.days[day]
        while session["duration_min"] > limit and (per_ex > 1 or any(a in patterns for a in ACCESSORY)):
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
    for _ in range(6):  # equivalentes ≈ duración − parte fija en Z1 (calentamientos): corrección aditiva
        candidate = engine.build_session(kind, sport, ep, total / mid, "base", week)
        if candidate["duration_min"] > cap:
            break
        session, got = candidate, equivalent_minutes(candidate)
        if got <= 0 or abs(got - equivalent) <= 0.1 * equivalent or total >= cap:
            break
        total = min(max(total + equivalent - got, 20), cap)
    if session is None:
        return None
    session["equivalent_min"] = round(equivalent_minutes(session))
    return session


def cardio_kinds(p: HealthProfile, n: int) -> list[str]:
    """Intensidad de las sesiones de la semana: moderada para principiantes y sin permiso de vigorosa; si no, polarizada
    (una de calidad cada 3 sesiones, como mucho dos). [MOTOR §1; Inferencia en el reparto]"""
    if p.level == "beginner" or p.intensity_cap != "vigorous" or n < 3:
        return ["aerobic"] * n
    quality = "vo2max" if p.goal == "fat_loss" else "threshold"
    hard = min(n // 3, 2)
    return [quality if i < hard else "aerobic" for i in range(n)]


# ---------------------------------------------------------------- semana completa

def _cardio(p: HealthProfile, start: date, dose: dict, days: list[str], strength: dict[str, dict]) -> tuple[dict, float]:
    """Resistencia de la semana (§5.3–§5.5): primero los días sin fuerza; si no llegan, también los de fuerza."""
    free = [d for d in days if d not in strength]
    slots = free + [d for d in strength if p.days[d] - strength[d]["duration_min"] >= 20]
    sports = p.cardio or ["walking"]
    pending, plan_days, done_eq = float(dose["aerobic"]), {}, 0.0
    caps = {d: p.days[d] - (strength[d]["duration_min"] if d in strength else 0) for d in slots}
    # Días duros y suaves alternos (monotonía de Foster < 2, MOTOR §3): entre los días libres, de más a menos tiempo,
    # 1,3 y 0,7 alternos; los días que ya tienen fuerza llevan menos aeróbico (0,5). [Inferencia en los factores]
    by_cap = sorted(free, key=lambda d: (-caps[d], WEEKDAYS.index(d)))
    weight = {d: caps[d] * (1.3 if by_cap.index(d) % 2 == 0 else 0.7) for d in free}
    weight |= {d: caps[d] * 0.5 for d in slots if d in strength}
    kinds = cardio_kinds(p, len(slots))
    for i, day in enumerate(slots):
        if pending <= 5:
            break
        cap = caps[day]
        # Reparto proporcional al peso de cada día que queda (§5.5); lo que no cabe pasa a los siguientes
        share = min(pending * weight[day] / sum(weight[d] for d in slots[i:]), cap)
        sport = sports[i % len(sports)]
        # En fuerza y músculo, nada de carrera el día de pierna si hay otra opción (Schumann/Lundberg 2022, §5.4)
        if day in strength and "sentadilla" in strength[day]["patterns"] and sport == "running":
            sport = next((s for s in sports if s != "running"), sport)
            if sport == "running" and p.goal == "strength":
                kinds[i] = "aerobic"
        week_no = WEEKDAYS.index(day) + start.isocalendar().week
        # Si el deporte no tiene ese tipo de calidad o no cabe, se prueba el otro antes de quedarse en aeróbico
        options = [kinds[i]] + [k for k in ("vo2max", "threshold") if kinds[i] != "aerobic" and k != kinds[i]] + ["aerobic"]
        session = next((s for k in options if (s := cardio_session(p, sport, k, share, week_no, cap))), None)
        if session:
            plan_days[day] = session
            pending -= session["equivalent_min"]
            done_eq += session["equivalent_min"]
    return plan_days, done_eq


STRENGTH_SHARES = (1.0, 0.85, 0.7, 0.55, 0.4, 0.3)  # parte del día que puede ocupar la fuerza [Inferencia]
MAX_MONOTONY = 2.0  # Foster 1998: por encima, la carga diaria es demasiado uniforme (MOTOR §3)
REST_NOTE = "Descanso extra: alternar días con y sin carga evita repetir el mismo esfuerzo todos los días."


def monotony(strength: dict[str, dict], cardio: dict[str, dict]) -> float:
    """Monotonía de Foster: media / desviación típica de la carga diaria de la semana (0 si no hay variación)."""
    loads = [sum(s["tss"] for s in (strength.get(d), cardio.get(d)) if s) for d in WEEKDAYS]
    mean = sum(loads) / 7
    sd = (sum((x - mean) ** 2 for x in loads) / 7) ** 0.5
    return mean / sd if sd else 0.0


def week_plan(p: HealthProfile, start: date, adherence: list[float] | None = None, week_in_block: int = 0) -> dict:
    """Plan de 7 días a partir de `start` (lunes). Devuelve la dosis, los días y los avisos."""
    adherence = adherence or []
    dose = week_dose(p, adherence, week_in_block)
    days = training_days(p)
    s_days = spread(days, dose["strength_days"])
    # Se prueban semanas candidatas en orden de preferencia y se elige la primera que cumple el suelo aeróbico de la OMS
    # (§1.2) con monotonía < 2 (§5.6). Orden: la fuerza ocupa primero todo el día y después menos (no se quita fuerza sin
    # necesidad); para cada parte, la semana tal cual y luego con un día libre más de descanso. Si ninguna baja de 2, se
    # queda la primera que cumple el suelo; si ninguna lo cumple, la de fuerza mínima.
    floor_eq = min(dose["aerobic"], FLOOR_AEROBIC)
    free = [d for d in days if d not in s_days]
    chosen = floor_ok = most = None
    for share in STRENGTH_SHARES:
        budget = {d: max(round(p.days[d] * share), 20) for d in s_days}
        strength = strength_week(p, s_days, dose["sets"], budget)
        for rest in [None] + free:
            plan_days, done_eq = _cardio(p, start, dose, [d for d in days if d != rest], strength)
            candidate = (strength, plan_days, done_eq, rest)
            if rest is None and (most is None or done_eq > most[2]):
                most = candidate  # sin llegar al suelo, nunca se quita un día disponible
            if done_eq < floor_eq:
                continue
            floor_ok = floor_ok or candidate
            if monotony(strength, plan_days) < MAX_MONOTONY:
                chosen = candidate
                break
        if chosen:
            break
    # Si ninguna semana llega al suelo, la que más minutos da con todos los días disponibles
    strength, plan_days, done_eq, extra_rest = chosen or floor_ok or most

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
        out.append({"date": day.isoformat(), "weekday": wd, "sessions": sessions}
                   | ({"note": REST_NOTE} if wd == extra_rest else {}))
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
            patterns = tuple(s["patterns"])
            if s["sets_per_exercise"] == 1:  # ya en 1 serie: fuera un accesorio
                patterns = tuple(pt for pt in patterns if pt != next((a for a in ACCESSORY if a in patterns), None))
            out.append(strength_session(p, patterns, max(s["sets_per_exercise"] - 1, 1), extra_rir=1))
        else:
            easier = cardio_session(p, s["sport"], "aerobic", s["equivalent_min"] * 0.7, week, s["duration_min"])
            out.append(easier or s)
    return out, "Hoy en versión suave: has dormido o te notas peor de lo normal."


# ---------------------------------------------------------------- §4 adherencia desde el historial

def position(block_start: date, monday: date) -> tuple[int, int]:
    """(bloque, semana dentro del bloque) de la semana que empieza en `monday`."""
    weeks = max((monday - block_start).days // 7, 0)
    return weeks // 4, weeks % 4


def week_with_changes(p: HealthProfile, start: date, adherence: list[float], week_in_block: int,
                      changes: dict[str, int] | None = None, since: date | None = None) -> dict:
    """Semana con cambios de disponibilidad (mejora 3 de replica/fixes.md): `changes` = minutos por día solo para esta
    semana (0 = ese día no puede). Se recalcula la semana con las mismas reglas; los días anteriores a `since` (cuando se
    hizo el cambio) se quedan como estaban, porque ya pasaron.

    shortcut: los días pasados siguen contando como disponibles al recalcular, así el reparto del resto no se dispara; y si
    hay varios cambios en una semana, los días entre el primero y el último usan el plan original. Guardar el historial de
    cambios si hace falta más precisión.
    """
    base = week_plan(p, start, adherence, week_in_block)
    if not changes:
        return base
    days = {d: m for d, m in (p.days | changes).items() if m}
    changed = week_plan(replace(p, days=days), start, adherence, week_in_block)
    cutoff = (since or start).isoformat()
    out = [b if b["date"] < cutoff else c for b, c in zip(base["days"], changed["days"])]
    eq = sum(s.get("equivalent_min", 0) for d in out for s in d["sessions"])
    return changed | {"days": out, "equivalent_min": round(eq), "changed": True}


def adherence_history(p: HealthProfile, block_start: date, done: dict[str, int], blocks: int,
                      changes: dict[str, tuple[dict[str, int], date]] | None = None) -> list[float]:
    """Adherencia de cada bloque terminado: sesiones hechas / planificadas (como mucho las planificadas cada día).

    Se reconstruye cada bloque con el plan que tocaba entonces, cambios de la semana incluidos (`changes`: lunes en ISO →
    (cambios, fecha del cambio)): el plan es determinista (§1).
    """
    changes = changes or {}
    history: list[float] = []
    for b in range(blocks):
        planned = completed = 0
        for w in range(4):
            monday = block_start + timedelta(weeks=4 * b + w)
            week_changes, since = changes.get(monday.isoformat(), (None, None))
            week = week_with_changes(p, monday, history, w, week_changes, since)
            for day in week["days"]:
                n = len(day["sessions"])
                planned += n
                completed += min(done.get(day["date"], 0), n)
        history.append(round(completed / planned, 3) if planned else 1.0)
    return history
