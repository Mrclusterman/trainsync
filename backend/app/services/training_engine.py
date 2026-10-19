"""Motor de entrenamiento de TrainSync (memoria técnica, sección 8).

Funciones puras y deterministas: no hacen I/O ni dependen de un LLM (DD-05).
Reglas y fuentes: docs/MOTOR_ENTRENAMIENTO.md (v0.2) y MEMORIA_TECNICA.md (v0.4).
Lo marcado con [Inferencia] no tiene evidencia publicada y es un parámetro ajustable.
"""
from dataclasses import dataclass, field
from datetime import date, timedelta
from math import exp, log
from statistics import mean, stdev

from app.services.session_catalog import CATALOG, CORE, LOWER, NOTES, PLYO, STRENGTH_DOSE, UPPER

K_CTL = 1 - exp(-1 / 42)
K_ATL = 1 - exp(-1 / 7)
WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
SWIM = ("swimming_pool", "swimming_open")


# ---------------------------------------------------------------- 8.1 / 8.2 carga

def hr_tss(duration_s: float, avg_hr: float, rest_hr: float, lthr: float) -> float:
    ratio = (avg_hr - rest_hr) / (lthr - rest_hr)
    return duration_s * ratio**2 * 100 / 3600


def power_tss(duration_s: float, np_watts: float, ftp: float) -> float:
    intensity = np_watts / ftp
    return duration_s * np_watts * intensity / (ftp * 3600) * 100


def swim_tss(duration_s: float, pace_100m_s: float, css_100m_s: float) -> float:
    ratio = css_100m_s / pace_100m_s
    return duration_s * ratio**2 * 100 / 3600


def strength_tss(rpe: float, minutes: float) -> float:
    return (rpe / 10) ** 2 * (minutes / 60) * 100


def next_load(prev: float, tss: float, k: float) -> float:
    """Un paso del EWA de CTL (k=K_CTL) o ATL (k=K_ATL)."""
    return prev + (tss - prev) * k


def hrv_status(history: list[float | None]) -> str | None:
    """Media móvil 7 d de ln(RMSSD) vs SWC (0,5 SD de los 28 días previos). Memoria 8.4 v0.4.

    history: RMSSD diario, del más antiguo al de hoy; None = sin medida.
    """
    ln = [log(v) if v else None for v in history[-35:]]
    last7 = [v for v in ln[-7:] if v is not None]
    base = [v for v in ln[:-7] if v is not None]
    if len(last7) < 3 or len(base) < 14:
        return None
    swc = 0.5 * stdev(base)
    return "within_swc" if abs(mean(last7) - mean(base)) <= swc else "outside_swc"


# ---------------------------------------------------------------- 8.3 zonas

# % LTHR según Friel; running y ciclismo tienen límites distintos (Memoria 8.3 v0.4)
HR_ZONES = {
    "running": {"Z1": (0, 0.85), "Z2": (0.85, 0.90), "Z3": (0.90, 0.95),
                "Z4": (0.95, 1.00), "Z5": (1.00, 1.07), "Z6": (1.07, None)},
    "cycling": {"Z1": (0, 0.81), "Z2": (0.81, 0.90), "Z3": (0.90, 0.94),
                "Z4": (0.94, 1.00), "Z5": (1.00, 1.07), "Z6": (1.07, None)},
}
# RPE (escala CR10) por zona. [Práctica; parcialmente verificado: 2 mmol ≈ CR10 4,3 y 4 mmol ≈ 6,5 en corredores]
# Varía mucho entre personas: es guía complementaria a FC/ritmo, no sustituto.
RPE_BY_ZONE = {"Z1": "1-2", "Z2": "3-4", "Z3": "5", "Z4": "6-7", "Z5": "8-9", "Z6": "10"}
SWIM_ZONES = {"Z1": (1.20, None), "Z2": (1.10, 1.20), "Z3": (1.03, 1.10),
              "Z4": (0.97, 1.03), "Z5": (0.90, 0.97), "Z6": (None, 0.90)}


def zone_target(zone: str, sport: str, lthr: float | None, css: float | None) -> str | None:
    """Rango legible de una zona: bpm para running/ciclismo, ritmo /100m para natación."""
    if sport in SWIM:
        if not css:  # Sin CSS se prescribe por RPE hasta hacer el test (MOTOR §4.3)
            return f"RPE {RPE_BY_ZONE[zone]}"
        lo, hi = SWIM_ZONES[zone]
        fmt = lambda f: f"{int(css * f // 60)}:{round(css * f % 60):02d}"
        if lo is None:
            return f"< {fmt(hi)}/100m"
        if hi is None:
            return f"> {fmt(lo)}/100m"
        return f"{fmt(lo)}-{fmt(hi)}/100m"
    if not lthr:
        return None
    lo, hi = HR_ZONES["running" if sport == "running" else "cycling"][zone]
    if hi is None:
        return f"> {round(lthr * lo)} ppm"
    return f"{round(lthr * lo)}-{round(lthr * hi) - 1} ppm" if lo else f"< {round(lthr * hi)} ppm"


def effort_mismatch(rpe: float | None, zone_secs: list[float]) -> bool:
    """RPE alto con FC baja: señal de fatiga (RPE más alto a igual intensidad en sobrecarga funcional).

    [Inferencia] umbrales: RPE >= 6 con menos del 10 % del tiempo en Z3 o superior.
    """
    total = sum(zone_secs or [])
    return bool(rpe and rpe >= 6 and total > 0 and sum(zone_secs[2:]) / total < 0.10)


def monotony(daily_loads: list[float]) -> tuple[float, float | None, float]:
    """Foster 1998: carga semanal (sRPE), monotonía (media/desv. típica diaria) y strain (carga × monotonía)."""
    load = sum(daily_loads)
    sd = stdev(daily_loads) if len(daily_loads) > 1 else 0
    mono = mean(daily_loads) / sd if sd else None
    return load, (round(mono, 2) if mono else None), (round(load * mono) if mono else 0)


def gray_zone(zone_secs: list[float]) -> bool:
    """Regla 2 de zona gris: Z3 > 15 % en una sesión fácil.

    [Inferencia] Sin plan guardado, "fácil" = menos del 5 % en Z4 o superior.
    Asume que el índice 2 de icu_hr_zone_times es la Z3 (tempo) del atleta.
    """
    total = sum(zone_secs)
    if total <= 0 or len(zone_secs) < 4:
        return False
    return zone_secs[2] / total > 0.15 and sum(zone_secs[3:]) / total < 0.05


# ---------------------------------------------------------------- 8.5 periodización

@dataclass
class Goal:
    name: str
    sport: str  # running / cycling / swimming_pool / swimming_open / triathlon / duathlon
    date: date
    priority: str = "A"
    distance_km: float | None = None
    trail: bool = False


def run_class(goal: "Goal | None") -> str | None:
    """Clase de prueba de running por distancia."""
    if goal is None or goal.sport != "running" or not goal.distance_km:
        return None
    d = goal.distance_km
    return "5k" if d <= 7 else "10k" if d <= 15 else "half" if d <= 25 else "marathon" if d <= 45 else "ultra"


# Días de taper (prioridad A) por distancia. [Práctica: fisioterapeutas de Mayo Clinic; ultra: entrenadores]
# Resto de pruebas: 14 días [Verificado: Bosquet 2007]
TAPER_DAYS = {"5k": 9, "10k": 9, "half": 12, "marathon": 21, "ultra": 21}


def taper_days(goal: Goal) -> int:
    if goal.priority == "B":
        return 4  # [Práctica] MOTOR §8.2
    if goal.priority == "C":
        return 1
    return TAPER_DAYS.get(run_class(goal), 14)


PREP_WEEKS = 20


def phase_for(day: date, goal: Goal | None, plan_start: date | None) -> tuple[str, bool]:
    """Devuelve (fase, semana_de_descarga). Fases: base_early, base, build, peak, taper."""
    if goal is None or day > goal.date:
        return "base", False  # sin objetivo: mantenimiento aeróbico
    taper = taper_days(goal)
    if (goal.date - day).days < taper:
        return "taper", False
    # [Inferencia] sin PLAN_START, preparación fija de 20 semanas; antes de ese inicio, base de mantenimiento.
    # Un inicio fijo hace que la fase avance día a día (antes, "hoy" era siempre el primer día del plan).
    start = plan_start or goal.date - timedelta(weeks=PREP_WEEKS)
    if day < start:
        return "base", False
    elapsed = (day - start).days
    # Proporciones de la Memoria 8.5 reescaladas sin el taper: base ~52 %, build ~28 %, peak ~20 %
    progress = elapsed / max((goal.date - start).days - taper, 1)
    week = elapsed // 7
    # Patrón 3+1: cada cuarta semana es de descarga, salvo en peak/taper
    recovery_week = week % 4 == 3
    if progress >= 0.80:
        return "peak", False
    if progress >= 0.52:
        return "build", recovery_week
    return ("base_early" if week < 4 else "base"), recovery_week


# Plantilla semanal polarizada (8.4). Lunes y días no listados = descanso. [Inferencia]
TEMPLATES = {
    "base_early": {"TUE": "vo2max", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "base": {"TUE": "vo2max", "WED": "aerobic", "THU": "neuromuscular", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "build": {"TUE": "threshold", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "peak": {"TUE": "race_specific", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "taper": {"TUE": "race_specific", "WED": "aerobic", "THU": "recovery", "SAT": "aerobic", "SUN": "aerobic"},
}
# Fuerza: más peso en base/construcción, menos en peak, nada en taper (RF-08)
STRENGTH_DAYS = {"base_early": ("TUE", "FRI"), "base": ("TUE", "FRI"), "build": ("TUE", "FRI"), "peak": ("TUE",), "taper": ()}
PHASE_VOLUME = {"base_early": 0.9, "base": 1.0, "build": 1.1, "peak": 1.15, "taper": 0.55}  # [Inferencia]
QUALITY = ("vo2max", "threshold", "race_specific", "brick", "test")
DOWNGRADE = {"vo2max": "aerobic", "threshold": "aerobic", "race_specific": "aerobic", "brick": "aerobic",
             "test": "aerobic", "neuromuscular": "aerobic", "long": "aerobic", "aerobic": "recovery",
             "recovery": "rest", "rest": "rest"}

# Deporte principal por día en objetivos multideporte (distribución aproximada de 8.4)
ROTATION = {
    "triathlon": {"TUE": "cycling", "WED": "swimming_pool", "THU": "running", "FRI": "swimming_pool", "SAT": "running", "SUN": "cycling"},
    "duathlon": {"TUE": "running", "WED": "cycling", "THU": "cycling", "FRI": "running", "SAT": "running", "SUN": "cycling"},
}


def template_for(phase: str, goal: Goal | None) -> dict:
    """Plantilla de la fase adaptada al tipo de prueba (MOTOR §6)."""
    t = dict(TEMPLATES[phase])
    rc = run_class(goal)
    if phase == "build" and rc in ("half", "marathon", "ultra"):
        t["THU"] = "threshold"  # [Verificado parcial: Casado 2022, fondistas de maratón más piramidales]
    if rc == "ultra" and phase in ("build", "peak"):
        t["SAT"] = "long"  # [Práctica] largas en días consecutivos; el volumen predice el rendimiento en ultra
    if goal and goal.sport in ROTATION and phase in ("build", "peak"):
        t["SAT"] = "brick"  # [Práctica] transiciones en triatlón/duatlón
    return t


# Tope de la sesión larga de running por distancia (min). [Práctica: Daniels <= 150; 5K-HM y ultra: Inferencia]
LONG_CAP = {"5k": 75, "10k": 90, "half": 120, "marathon": 150, "ultra": 240}


def long_cap(goal: Goal | None, sport: str) -> int | None:
    return LONG_CAP.get(run_class(goal), 150) if sport == "running" else None


# ---------------------------------------------------------------- 8.4 decisión diaria

@dataclass
class DayState:
    ctl: float
    atl: float
    hrv_history: list[float | None] = field(default_factory=list)  # RMSSD diario hasta hoy
    sleep_score: float | None = None
    consecutive_days: int = 0
    effort_mismatch: bool = False  # RPE alto con FC baja en los últimos 2 días

    @property
    def tsb(self) -> float:
        return self.ctl - self.atl


def adjust(session_type: str, state: DayState) -> tuple[str, float, list[str]]:
    """Aplica el algoritmo de decisión diaria. Devuelve (tipo, factor_volumen, motivos)."""
    reasons, volume, tsb = [], 1.0, state.tsb
    status = hrv_status(state.hrv_history)
    # TSB entre -10 y -30 es zona productiva de carga (Friel); solo < -30 obliga a recuperar
    if tsb < -30:
        return ("recovery" if session_type != "rest" else "rest"), 1.0, [f"TSB {tsb:.1f} < -30: riesgo alto, solo recuperación"]
    if status == "outside_swc" and session_type in QUALITY:
        session_type = "aerobic"
        reasons.append("HRV fuera de tu rango normal (media 7 días): hoy sin intensidad")
    if state.effort_mismatch and session_type in QUALITY:
        session_type = "aerobic"
        reasons.append("Esfuerzo percibido alto con FC baja en tu última sesión: posible fatiga, hoy sin intensidad")
    if state.sleep_score is not None and state.sleep_score < 50:
        # Mal sueño quita la calidad; sumado a HRV fuera de rango, el día pasa a recuperación (criterio Fase 3)
        if session_type in QUALITY or status == "outside_swc":
            session_type = DOWNGRADE[session_type]
        volume = 0.7
        reasons.append(f"Sueño {state.sleep_score:.0f}/100: -30 % de carga y sin calidad")
    if state.consecutive_days >= 3 and session_type in QUALITY and tsb < -20:  # [Inferencia]
        session_type = "aerobic"
        reasons.append(f"{state.consecutive_days} días seguidos entrenando con TSB {tsb:.1f}: sin calidad hoy")
    return session_type, volume, reasons


# ---------------------------------------------------------------- sesiones

@dataclass
class Profile:
    active_sports: list[str]
    strength: str = "gym"  # gym / bodyweight / none
    rest_days: list[str] = field(default_factory=lambda: ["MON"])
    lthr: float | None = None  # LTHR por defecto; lthr_by_sport la sustituye por deporte
    lthr_by_sport: dict[str, float] = field(default_factory=dict)
    max_hr: float | None = None
    rest_hr: float | None = None
    css: float | None = None  # seg/100m
    plan_start: date | None = None
    warnings: list[str] = field(default_factory=list)
    needs_test: set[str] = field(default_factory=set)  # deportes sin LTHR/CSS fiable (onboarding RF-03)

    def lthr_for(self, sport: str) -> float | None:
        return self.lthr_by_sport.get(sport) or self.lthr


# Ratio de intensidad medio por zona para estimar TSS planificado. [Inferencia]
ZONE_IF = {"Z1": 0.65, "Z2": 0.75, "Z3": 0.88, "Z4": 0.97, "Z5": 1.05, "Z6": 1.15}
NAMES = {"recovery": "Recuperación", "aerobic": "Aeróbico base", "long": "Tirada larga",
         "vo2max": "Intervalos VO2max", "threshold": "Umbral", "race_specific": "Específico de carrera",
         "rest": "Descanso", "strength": "Fuerza", "neuromuscular": "Neuromuscular", "brick": "Brick (transición)",
         "test": "Test de campo"}
GOALS = {"recovery": "Recuperar sin generar fatiga", "aerobic": "Base aeróbica y eficiencia (Z2 pura, sin zona gris)",
         "long": "Resistencia aeróbica y economía", "vo2max": "Elevar el techo aeróbico",
         "threshold": "Subir el umbral de lactato", "race_specific": "Ritmo y sensaciones de competición",
         "neuromuscular": "Potencia y economía sin fatiga (esfuerzos muy cortos, recuperación completa)",
         "brick": "Acostumbrar las piernas a correr tras la bici", "test": "Medir LTHR o CSS para fijar tus zonas"}


def _blocks_tss(blocks: list[dict]) -> float:
    total = 0.0
    for b in blocks:
        duty = b.get("duty", 1)  # intermitentes (30/15): solo esa fracción va a la zona de trabajo
        total += b["reps"] * b["minutes"] * (duty * ZONE_IF[b["zone"]] ** 2 + (1 - duty) * ZONE_IF["Z1"] ** 2)
        total += b["reps"] * b.get("rest_minutes", 0) * ZONE_IF[b.get("rest_zone", "Z1")] ** 2
    return total / 60 * 100


def pick_session(kind: str, sport: str, phase: str, week: int, event: str | None = None) -> dict | None:
    """Sesión del catálogo para el slot; rota cada semana entre las opciones válidas (determinista).

    Las sesiones de un tipo de prueba (trail) solo salen para ese tipo, alternando con las generales. [Inferencia]
    """
    options = [c for c in CATALOG if c["sport"] == sport and c["category"] == kind and phase in c["phases"]
               and (not c.get("events") or event in c["events"])]
    options.sort(key=lambda c: not c.get("events"))
    return options[week % len(options)] if options else None


def build_session(kind: str, sport: str, profile: Profile, volume: float = 1.0, phase: str = "base", week: int = 0,
                  event: str | None = None, max_minutes: int | None = None) -> dict | None:
    """Sesión del catálogo con duración ajustada al volumen; None si el deporte no tiene sesión para ese slot."""
    entry = pick_session(kind, sport, phase, week, event)
    if entry is None:
        return None
    lo, hi = entry["duration"]
    total = max(round((lo + hi) / 2 * volume), 20)
    if max_minutes and kind == "long":
        total = min(total, max_minutes)
    blocks = [dict(b) for b in entry["blocks"]]
    span = lambda b: b["reps"] * (b["minutes"] + b.get("rest_minutes", 0))
    for b in blocks:
        if b["reps"] > 1 and b["minutes"] is not None:
            # Si el volumen no da para la estructura (p. ej. alternativa de igual TSS), caben menos series:
            # mínimo 2 en series cortas y 1 en bloques de >= 10 min (una serie de 13' de 30/15 es válida)
            others = sum(span(o) for o in blocks if o is not b and o["minutes"] is not None)
            fits = int((total - 5 - others) // (b["minutes"] + b.get("rest_minutes", 0)))
            b["reps"] = max(1 if b["minutes"] >= 10 else 2, min(b["reps"], fits))
    fixed = sum(span(b) for b in blocks if b["minutes"] is not None)
    for b in blocks:
        if b["minutes"] is None:
            b["minutes"] = max(total - fixed, 5)
    for b in blocks:
        b["rpe"] = RPE_BY_ZONE[b["zone"]]
        if b["reps"] > 1 and b["minutes"] < 3 and b["zone"] in ("Z5", "Z6"):
            b["guide"] = "Guíate por RPE: en series cortas la FC tarda en subir"  # [Práctica]
    zones = {b["zone"] for b in blocks} | {b["rest_zone"] for b in blocks if "rest_zone" in b}
    lthr = profile.lthr_for(sport)
    # Un test se hace al máximo sostenible: un objetivo calculado con la LTHR que se quiere medir lo sesgaría
    targets = {} if kind == "test" else {z: zone_target(z, sport, lthr, profile.css) for z in sorted(zones)}
    # Techo de sesión fácil = umbral aeróbico estimado (LTHR - 30, Friel) hasta que se mida
    hr_cap = round(lthr - 30) if lthr and kind not in QUALITY and sport not in SWIM else None
    if hr_cap and targets.get("Z2"):
        # El techo manda sobre la Z2 de Friel: en running el techo (LTHR-30) cae por debajo del inicio de Z2
        z2_lo = round(lthr * HR_ZONES["running" if sport == "running" else "cycling"]["Z2"][0])
        targets["Z2"] = f"{z2_lo}-{hr_cap} ppm" if z2_lo < hr_cap else f"<= {hr_cap} ppm"
        if targets.get("Z1") and z2_lo > hr_cap:
            targets["Z1"] = f"< {hr_cap} ppm"
    return {
        "id": entry["id"], "sport": sport, "type": kind, "name": NAMES[kind], "goal": GOALS[kind],
        "duration_min": round(sum(b["reps"] * (b["minutes"] + b.get("rest_minutes", 0)) for b in blocks)),
        "tss": round(_blocks_tss(blocks)), "blocks": blocks,
        "targets": {z: t for z, t in targets.items() if t}, "hr_cap": hr_cap,
        "if_bad": NOTES[kind][0], "if_good": NOTES[kind][1],
    }


def build_strength(goal_sport: str, profile: Profile, phase: str) -> dict:
    """Fuerza por fase (MOTOR §5). Carga = rTSS con el RPE planificado."""
    dose, rpe, minutes = STRENGTH_DOSE[phase]
    kit = "gym" if profile.strength == "gym" else "bodyweight"
    upper = goal_sport in SWIM or goal_sport == "triathlon"
    exercises = LOWER[kit] + (PLYO[kit] if phase == "build" else []) + (UPPER[kit] if upper else []) + CORE
    # Eddens 2018: fuerza antes favorece la fuerza; en build/peak prima la calidad de la sesión de resistencia
    order = "antes" if phase in ("base_early", "base") else "después"
    return {"sport": "strength", "type": "strength", "name": NAMES["strength"], "duration_min": minutes,
            "dose": dose, "rpe_planned": rpe, "tss": round(strength_tss(rpe, minutes)), "exercises": exercises,
            "order": f"{order} de la sesión de resistencia si van seguidas"}


def main_sport(weekday: str, goal: Goal | None, profile: Profile) -> str:
    preferred = ROTATION.get(goal.sport, {}).get(weekday) if goal else None
    if preferred is None and goal and goal.sport in profile.active_sports:
        preferred = goal.sport
    if preferred in profile.active_sports:
        return preferred
    return profile.active_sports[0]


def propose_day(day: date, profile: Profile, goal: Goal | None, state: DayState | None = None,
                scale: float = 1.0) -> dict:
    """Propuesta diaria (RF-05): sesión principal + alternativas por deporte activo + fuerza."""
    weekday = WEEKDAYS[day.weekday()]
    week = day.isocalendar().week
    phase, recovery_week = phase_for(day, goal, profile.plan_start)
    kind = "rest" if weekday in profile.rest_days else template_for(phase, goal).get(weekday, "rest")
    volume, reasons, why = PHASE_VOLUME[phase] * scale, [], []
    if recovery_week:
        volume *= 0.7
        if kind in QUALITY and weekday == "THU":
            kind = "aerobic"
        reasons.append("Semana de descarga (patrón 3+1): -30 % de volumen")
    if state is not None:
        kind, factor, why = adjust(kind, state)
        volume *= factor
        reasons += why

    out = {"date": day.isoformat(), "weekday": weekday, "phase": phase, "recovery_week": recovery_week,
           "reasons": reasons, "main": None, "alternatives": [], "strength": None}
    if kind == "rest":
        return out
    preferred = "cycling" if kind == "brick" else main_sport(weekday, goal, profile)
    event = "trail" if goal and goal.trail else None
    sessions = {s: build_session(kind, s, profile, volume, phase, week, event, long_cap(goal, s))
                for s in [preferred] + [s for s in profile.active_sports if s != preferred]}
    sessions = {s: v for s, v in sessions.items() if v}
    if not sessions:
        return out
    sport, out["main"] = next(iter(sessions.items()))
    target = out["main"]["tss"]
    for other, alt in list(sessions.items())[1:]:
        # Alternativa equivalente en carga (±15 %): el TSS no es lineal con la duración, se itera
        alt_volume = volume
        for _ in range(3):
            if not alt["tss"] or abs(alt["tss"] - target) <= 0.15 * target:
                break
            alt_volume *= target / alt["tss"]
            alt = build_session(kind, other, profile, alt_volume, phase, week, event, long_cap(goal, other))
        out["alternatives"].append(alt)
    # Sin fuerza si el estado del día ha obligado a bajar la carga
    if profile.strength != "none" and weekday in STRENGTH_DAYS[phase] and not why:
        out["strength"] = build_strength(goal.sport if goal else sport, profile, phase)
    return out


# Rampa de CTL objetivo por semana según el CTL actual (MOTOR §9.1) [Práctica: TrainingPeaks]
RAMP = ((45, 3), (70, 4), (float("inf"), 5))


def weekly_tss_target(ctl: float, recovery_week: bool = False) -> float:
    """TSS semanal para subir el CTL la rampa objetivo: 7 × (CTL + r / (1 - e^(-7/42)))."""
    ramp = next(r for limit, r in RAMP if ctl <= limit)
    target = 7 * (ctl + ramp / (1 - exp(-7 / 42)))
    return target * 0.65 if recovery_week else target  # [Práctica] descarga = -35 %


def day_tss(day: dict) -> float:
    return sum(s["tss"] for s in (day["main"], day["strength"]) if s)


# Peso de cada tipo de sesión en el TSS semanal (MOTOR §9.1). [Inferencia]
WEIGHTS = {"long": 25, "vo2max": 15, "threshold": 15, "race_specific": 15, "brick": 15, "test": 15,
           "aerobic": 11, "neuromuscular": 11, "recovery": 5}


def plan(start: date, days: int, profile: Profile, goal: Goal | None, today_state: DayState | None = None) -> list[dict]:
    """Plan de N días: TSS semanal según la rampa de CTL, repartido por tipo de sesión. Solo el día 1 usa el estado real."""
    phase, recovery_week = phase_for(start, goal, profile.plan_start)
    scales = {}
    if today_state is not None and phase != "taper":  # el taper ya fija su propio volumen
        week = [propose_day(start + timedelta(d), profile, goal) for d in range(7)]
        mains = [d for d in week if d["main"] and d["main"]["tss"]]
        target = weekly_tss_target(today_state.ctl, recovery_week) - sum(d["strength"]["tss"] for d in week if d["strength"])
        total_weight = sum(WEIGHTS[d["main"]["type"]] for d in mains)
        for d in mains:
            day_target = target * WEIGHTS[d["main"]["type"]] / total_weight
            # [Inferencia] límites 0,5-1,5 para no deformar las sesiones del catálogo
            scales[d["weekday"]] = min(max(day_target / d["main"]["tss"], 0.5), 1.5)
    # shortcut: días 8-14 usan la escala del CTL de hoy; proyectar CTL por semana cuando se muestre el TSB proyectado
    out = [propose_day(day, profile, goal, today_state if d == 0 else None, scales.get(WEEKDAYS[day.weekday()], 1.0))
           for d in range(days) for day in [start + timedelta(d)]]
    schedule_tests(out, profile)
    return out


def schedule_tests(days: list[dict], profile: Profile) -> None:
    """Onboarding (RF-03): la primera sesión de calidad pasa a ser el test del deporte sin LTHR/CSS fiable."""
    used = set()
    for sport in [s for s in profile.active_sports if s in profile.needs_test]:
        quality = [d for d in days if d["main"] and d["main"]["type"] in QUALITY and d["date"] not in used]
        day = next((d for d in quality if d["main"]["sport"] == sport), quality[0] if quality else None)
        test = day and build_session("test", sport, profile, phase=day["phase"])
        if test:
            day["alternatives"].insert(0, day["main"])
            day["main"] = test
            day["reasons"].append(f"Test de {sport}: faltan datos fisiológicos fiables para fijar tus zonas")
            used.add(day["date"])
