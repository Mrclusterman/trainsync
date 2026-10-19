"""Motor de entrenamiento de TrainSync (memoria técnica, sección 8).

Funciones puras y deterministas: no hacen I/O ni dependen de un LLM (DD-05).
Reglas y fuentes: docs/MOTOR_ENTRENAMIENTO.md (v0.2) y MEMORIA_TECNICA.md (v0.4).
Lo marcado con [Inferencia] no tiene evidencia publicada y es un parámetro ajustable.
"""
from dataclasses import dataclass, field
from datetime import date, timedelta
from math import exp, log
from statistics import mean, stdev

from app.services.session_catalog import CATALOG, CORE, LOWER, PLYO, STRENGTH_DOSE, UPPER

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
SWIM_RPE = {"Z1": "RPE 2-3", "Z2": "RPE 4-5", "Z3": "RPE 6", "Z4": "RPE 7", "Z5": "RPE 8-9", "Z6": "RPE 10"}
SWIM_ZONES = {"Z1": (1.20, None), "Z2": (1.10, 1.20), "Z3": (1.03, 1.10),
              "Z4": (0.97, 1.03), "Z5": (0.90, 0.97), "Z6": (None, 0.90)}


def zone_target(zone: str, sport: str, lthr: float | None, css: float | None) -> str | None:
    """Rango legible de una zona: bpm para running/ciclismo, ritmo /100m para natación."""
    if sport in SWIM:
        if not css:  # Sin CSS se prescribe por RPE hasta hacer el test (MOTOR §4.3)
            return SWIM_RPE[zone]
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


def phase_for(day: date, goal: Goal | None, plan_start: date | None) -> tuple[str, bool]:
    """Devuelve (fase, semana_de_descarga). Fases: base_early, base, build, peak, taper."""
    if goal is None or day > goal.date:
        return "base", False  # sin objetivo: mantenimiento aeróbico
    # [Inferencia] sin PLAN_START se asume una preparación de al menos 12 semanas
    start = plan_start or min(day, goal.date - timedelta(weeks=12))
    total = max((goal.date - start).days, 1)
    elapsed = (day - start).days
    progress = elapsed / total
    week = elapsed // 7
    # Patrón 3+1: cada cuarta semana es de descarga, salvo en peak/taper
    recovery_week = week % 4 == 3
    if progress >= 0.87:
        return "taper", False
    if progress >= 0.67:
        return "peak", False
    if progress >= 0.45:
        return "build", recovery_week
    return ("base_early" if week < 4 else "base"), recovery_week


# Plantilla semanal polarizada (8.4). Lunes y días no listados = descanso. [Inferencia]
TEMPLATES = {
    "base_early": {"TUE": "vo2max", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "base": {"TUE": "vo2max", "WED": "aerobic", "THU": "aerobic", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "build": {"TUE": "threshold", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "peak": {"TUE": "race_specific", "WED": "aerobic", "THU": "vo2max", "FRI": "recovery", "SAT": "aerobic", "SUN": "long"},
    "taper": {"TUE": "race_specific", "WED": "aerobic", "THU": "recovery", "SAT": "aerobic", "SUN": "aerobic"},
}
# Fuerza: más peso en base/construcción, menos en peak, nada en taper (RF-08)
STRENGTH_DAYS = {"base_early": ("TUE", "FRI"), "base": ("TUE", "FRI"), "build": ("TUE", "FRI"), "peak": ("TUE",), "taper": ()}
PHASE_VOLUME = {"base_early": 0.9, "base": 1.0, "build": 1.1, "peak": 1.15, "taper": 0.55}  # [Inferencia]
QUALITY = ("vo2max", "threshold", "race_specific")
DOWNGRADE = {"vo2max": "aerobic", "threshold": "aerobic", "race_specific": "aerobic",
             "long": "aerobic", "aerobic": "recovery", "recovery": "rest", "rest": "rest"}

# Deporte principal por día en objetivos multideporte (distribución aproximada de 8.4)
ROTATION = {
    "triathlon": {"TUE": "cycling", "WED": "swimming_pool", "THU": "running", "FRI": "swimming_pool", "SAT": "running", "SUN": "cycling"},
    "duathlon": {"TUE": "running", "WED": "cycling", "THU": "cycling", "FRI": "running", "SAT": "running", "SUN": "cycling"},
}


# ---------------------------------------------------------------- 8.4 decisión diaria

@dataclass
class DayState:
    ctl: float
    atl: float
    hrv_history: list[float | None] = field(default_factory=list)  # RMSSD diario hasta hoy
    sleep_score: float | None = None
    consecutive_days: int = 0

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

    def lthr_for(self, sport: str) -> float | None:
        return self.lthr_by_sport.get(sport) or self.lthr


# Ratio de intensidad medio por zona para estimar TSS planificado. [Inferencia]
ZONE_IF = {"Z1": 0.65, "Z2": 0.75, "Z3": 0.88, "Z4": 0.97, "Z5": 1.05, "Z6": 1.15}
NAMES = {"recovery": "Recuperación", "aerobic": "Aeróbico base", "long": "Tirada larga",
         "vo2max": "Intervalos VO2max", "threshold": "Umbral", "race_specific": "Específico de carrera",
         "rest": "Descanso", "strength": "Fuerza"}
GOALS = {"recovery": "Recuperar sin generar fatiga", "aerobic": "Base aeróbica y eficiencia (Z2 pura, sin zona gris)",
         "long": "Resistencia aeróbica y economía", "vo2max": "Elevar el techo aeróbico",
         "threshold": "Subir el umbral de lactato", "race_specific": "Ritmo y sensaciones de competición"}


def _blocks_tss(blocks: list[dict]) -> float:
    total = 0.0
    for b in blocks:
        total += b["reps"] * b["minutes"] * ZONE_IF[b["zone"]] ** 2
        total += b["reps"] * b.get("rest_minutes", 0) * ZONE_IF[b.get("rest_zone", "Z1")] ** 2
    return total / 60 * 100


def pick_session(kind: str, sport: str, phase: str, week: int) -> dict | None:
    """Sesión del catálogo para el slot; rota cada semana entre las opciones válidas (determinista)."""
    options = [c for c in CATALOG if c["sport"] == sport and c["category"] == kind and phase in c["phases"]]
    return options[week % len(options)] if options else None


def build_session(kind: str, sport: str, profile: Profile, volume: float = 1.0,
                  phase: str = "base", week: int = 0) -> dict | None:
    """Sesión del catálogo con duración ajustada al volumen; None si el deporte no tiene sesión para ese slot."""
    entry = pick_session(kind, sport, phase, week)
    if entry is None:
        return None
    lo, hi = entry["duration"]
    total = max(round((lo + hi) / 2 * volume), 20)
    if sport == "running" and kind == "long":
        total = min(total, 150)  # [Práctica: Daniels] tirada larga <= 150 min
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
    zones = {b["zone"] for b in blocks} | {b["rest_zone"] for b in blocks if "rest_zone" in b}
    lthr = profile.lthr_for(sport)
    targets = {z: zone_target(z, sport, lthr, profile.css) for z in sorted(zones)}
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
    kind = "rest" if weekday in profile.rest_days else TEMPLATES[phase].get(weekday, "rest")
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
    preferred = main_sport(weekday, goal, profile)
    sessions = {s: build_session(kind, s, profile, volume, phase, week)
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
            alt = build_session(kind, other, profile, alt_volume, phase, week)
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


def plan(start: date, days: int, profile: Profile, goal: Goal | None, today_state: DayState | None = None) -> list[dict]:
    """Plan de N días escalado a la rampa de CTL. Solo el primer día se ajusta con el estado real."""
    scale = 1.0
    phase, recovery_week = phase_for(start, goal, profile.plan_start)
    if today_state is not None and phase != "taper":  # el taper ya fija su propio volumen
        template = sum(day_tss(propose_day(start + timedelta(d), profile, goal)) for d in range(7))
        if template:
            # [Inferencia] límites 0,5-1,5 para no deformar las sesiones del catálogo
            scale = min(max(weekly_tss_target(today_state.ctl, recovery_week) / template, 0.5), 1.5)
    # shortcut: días 8-14 usan la escala del CTL de hoy; proyectar CTL por semana cuando se muestre el TSB proyectado
    # shortcut: escala uniforme, sin los pesos por tipo de sesión de MOTOR §9.1 (la larga pesa ~40 %); aplicarlos si la larga resulta excesiva
    return [propose_day(start + timedelta(d), profile, goal, today_state if d == 0 else None, scale) for d in range(days)]
