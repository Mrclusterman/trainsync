"""Motor de entrenamiento de TrainSync (memoria técnica, sección 8).

Funciones puras y deterministas: no hacen I/O ni dependen de un LLM (DD-05).
Reglas y fuentes: docs/MOTOR_ENTRENAMIENTO.md (v0.2) y MEMORIA_TECNICA.md (v0.4).
Lo marcado con [Inferencia] no tiene evidencia publicada y es un parámetro ajustable.
"""
from dataclasses import dataclass, field
from datetime import date, timedelta
from math import exp, log
from statistics import mean, stdev

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
SWIM_ZONES = {"Z1": (1.20, None), "Z2": (1.10, 1.20), "Z3": (1.03, 1.10),
              "Z4": (0.97, 1.03), "Z5": (0.90, 0.97), "Z6": (None, 0.90)}


def zone_target(zone: str, sport: str, lthr: float | None, css: float | None) -> str | None:
    """Rango legible de una zona: bpm para running/ciclismo, ritmo /100m para natación."""
    if sport in SWIM:
        if not css:
            return None
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


# Minutos base por tipo y deporte. [Inferencia] Se ajustarán con PROMPT_MOTOR_ENTRENAMIENTO.md
BASE_MINUTES = {
    "running": {"recovery": 30, "aerobic": 50, "long": 90, "vo2max": 55, "threshold": 60, "race_specific": 70},
    "cycling": {"recovery": 45, "aerobic": 90, "long": 180, "vo2max": 75, "threshold": 90, "race_specific": 120},
    "swim": {"recovery": 30, "aerobic": 45, "long": 60, "vo2max": 45, "threshold": 50, "race_specific": 60},
}
# Series principales (reps, min trabajo, zona, min recuperación). VO2max según 8.4 (5x3, 8x2)
MAIN_SETS = {
    "running": {"vo2max": (5, 3, "Z5", 3), "threshold": (3, 10, "Z4", 3), "race_specific": (2, 15, "Z4", 4)},
    "cycling": {"vo2max": (5, 4, "Z5", 4), "threshold": (3, 12, "Z4", 4), "race_specific": (2, 20, "Z4", 5)},
    "swim": {"vo2max": (8, 2, "Z5", 1), "threshold": (5, 4, "Z4", 1), "race_specific": (3, 8, "Z4", 2)},
}
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


def build_session(kind: str, sport: str, profile: Profile, volume: float = 1.0) -> dict:
    """Sesión aeróbica con bloques calentamiento / principal / vuelta a la calma."""
    lib = "swim" if sport in SWIM else sport
    total = max(round(BASE_MINUTES[lib][kind] * volume), 20)
    if kind in MAIN_SETS[lib]:
        reps, work, zone, rest = MAIN_SETS[lib][kind]
        main = {"block": "principal", "reps": reps, "minutes": work, "zone": zone, "rest_minutes": rest, "rest_zone": "Z1"}
        easy = max(total - reps * (work + rest), 15)
        blocks = [{"block": "calentamiento", "reps": 1, "minutes": round(easy * 0.6), "zone": "Z2"}, main,
                  {"block": "vuelta a la calma", "reps": 1, "minutes": round(easy * 0.4), "zone": "Z1"}]
    else:
        zone = "Z1" if kind == "recovery" else "Z2"
        blocks = [{"block": "calentamiento", "reps": 1, "minutes": 10, "zone": "Z1"},
                  {"block": "principal", "reps": 1, "minutes": max(total - 15, 5), "zone": zone},
                  {"block": "vuelta a la calma", "reps": 1, "minutes": 5, "zone": "Z1"}]
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
        "sport": sport, "type": kind, "name": NAMES[kind], "goal": GOALS[kind],
        "duration_min": sum(b["reps"] * (b["minutes"] + b.get("rest_minutes", 0)) for b in blocks),
        "tss": round(_blocks_tss(blocks)), "blocks": blocks,
        "targets": {z: t for z, t in targets.items() if t}, "hr_cap": hr_cap,
    }


def build_strength(goal_sport: str, profile: Profile, phase: str) -> dict:
    """Fuerza adaptada al deporte (RF-08). Carga = rTSS con el RPE planificado."""
    upper = goal_sport in SWIM or goal_sport == "triathlon"
    gym = profile.strength == "gym"
    exercises = (["Sentadilla con barra 4x6", "Peso muerto rumano 3x8", "Zancada búlgara 3x8/pierna", "Elevación de gemelo 3x12"]
                 if gym else ["Sentadilla goblet o búlgara 4x10", "Puente de glúteo a una pierna 3x12", "Step-up 3x10/pierna", "Gemelo a una pierna 3x15"])
    if upper:
        exercises += (["Jalón al pecho 3x8", "Remo con mancuerna 3x10"] if gym else ["Dominadas o remo invertido 3x8", "Flexiones 3x12"])
    exercises += ["Plancha frontal 3x40 s", "Pallof press o plancha lateral 3x30 s/lado"]
    minutes, rpe = (40, 6) if phase == "peak" else (45, 7)
    return {"sport": "strength", "type": "strength", "name": NAMES["strength"], "duration_min": minutes,
            "rpe_planned": rpe, "tss": round(strength_tss(rpe, minutes)), "exercises": exercises}


def main_sport(weekday: str, goal: Goal | None, profile: Profile) -> str:
    preferred = ROTATION.get(goal.sport, {}).get(weekday) if goal else None
    if preferred is None and goal and goal.sport in profile.active_sports:
        preferred = goal.sport
    if preferred in profile.active_sports:
        return preferred
    return profile.active_sports[0]


def propose_day(day: date, profile: Profile, goal: Goal | None, state: DayState | None = None) -> dict:
    """Propuesta diaria (RF-05): sesión principal + alternativas por deporte activo + fuerza."""
    weekday = WEEKDAYS[day.weekday()]
    phase, recovery_week = phase_for(day, goal, profile.plan_start)
    kind = "rest" if weekday in profile.rest_days else TEMPLATES[phase].get(weekday, "rest")
    volume, reasons, why = PHASE_VOLUME[phase], [], []
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
    sport = main_sport(weekday, goal, profile)
    out["main"] = build_session(kind, sport, profile, volume)
    for other in profile.active_sports:
        if other == sport:
            continue
        # Alternativa equivalente en carga: se escala el volumen hasta igualar el TSS
        alt = build_session(kind, other, profile, volume)
        if alt["tss"]:
            alt = build_session(kind, other, profile, volume * out["main"]["tss"] / alt["tss"])
        out["alternatives"].append(alt)
    # Sin fuerza si el estado del día ha obligado a bajar la carga
    if profile.strength != "none" and weekday in STRENGTH_DAYS[phase] and not why:
        out["strength"] = build_strength(goal.sport if goal else sport, profile, phase)
    return out


def plan(start: date, days: int, profile: Profile, goal: Goal | None, today_state: DayState | None = None) -> list[dict]:
    """Plan de N días. Solo el primer día se ajusta con el estado real; el resto se recalcula a diario."""
    return [propose_day(start + timedelta(d), profile, goal, today_state if d == 0 else None) for d in range(days)]
