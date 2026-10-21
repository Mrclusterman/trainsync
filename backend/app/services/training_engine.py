"""Motor de entrenamiento de TrainSync (memoria técnica, sección 8).

Funciones puras y deterministas: no hacen I/O ni dependen de un LLM (DD-05).
Reglas y fuentes: docs/MOTOR_ENTRENAMIENTO.md (v0.2) y MEMORIA_TECNICA.md (v0.4).
Lo marcado con [Inferencia] no tiene evidencia publicada y es un parámetro ajustable.
"""
from dataclasses import dataclass, field
from datetime import date, timedelta
from math import exp, log
from statistics import linear_regression, median, mean, stdev

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


# Plantilla semanal polarizada (8.4). Lunes y días no listados = descanso; fit_days la ajusta a los días del usuario.
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
QUALITY = ("vo2max", "threshold", "race_specific", "brick", "test", "tempo", "anaerobic")
DOWNGRADE = {"vo2max": "aerobic", "threshold": "aerobic", "race_specific": "aerobic", "brick": "aerobic",
             "test": "aerobic", "neuromuscular": "aerobic", "long": "aerobic", "aerobic": "recovery",
             "recovery": "rest", "rest": "rest", "tempo": "aerobic", "anaerobic": "aerobic"}

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


# Qué se conserva antes cuando no caben todas las sesiones en los días disponibles [Inferencia]
KEEP_FIRST = ("long", "race_specific", "brick", "threshold", "vo2max", "test", "tempo", "anaerobic", "neuromuscular",
              "aerobic", "recovery")
DAY_NAMES = dict(zip(WEEKDAYS, ("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")))


def fit_days(template: dict, rest_days: list[str]) -> tuple[dict, dict]:
    """Coloca la plantilla en los días que el usuario puede entrenar (mejora 4 de replica/fixes.md).

    Lo que cae en un día sin entreno pasa al día libre más cercano; si no queda ninguno, desplaza a la sesión menos
    importante (KEEP_FIRST). Devuelve la plantilla colocada y, por día, de qué día viene lo movido.
    """
    available = [d for d in WEEKDAYS if d not in rest_days]
    placed = {d: k for d, k in template.items() if d in available}
    moved: dict[str, str] = {}
    for day in sorted((d for d in template if d not in available), key=lambda d: KEEP_FIRST.index(template[d])):
        free = [d for d in available if d not in placed]
        if free:
            target = min(free, key=lambda d: abs(WEEKDAYS.index(d) - WEEKDAYS.index(day)))
        else:
            target = max(placed, key=lambda d: KEEP_FIRST.index(placed[d]), default=None)
            if target is None or KEEP_FIRST.index(placed[target]) <= KEEP_FIRST.index(template[day]):
                continue
        placed[target], moved[target] = template[day], day
    return placed, moved


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
    load_focus: dict[str, float] | None = None  # reparto de la carga de 28 días (ver load_focus)
    days_since_activity: int | None = None

    @property
    def tsb(self) -> float:
        return self.ctl - self.atl


def adjust(session_type: str, state: DayState) -> tuple[str, float, list[str]]:
    """Aplica el algoritmo de decisión diaria. Devuelve (tipo, factor_volumen, motivos)."""
    reasons, volume, tsb = [], 1.0, state.tsb
    status = hrv_status(state.hrv_history)
    # TSB entre -10 y -30 es zona productiva de carga (Friel); solo < -30 obliga a recuperar
    if tsb < -30:
        return ("recovery" if session_type != "rest" else "rest"), 1.0, [f"TSB {tsb:.1f} < -30: riesgo alto, solo recuperación [Práctica]"]
    if status == "outside_swc" and session_type in QUALITY:
        session_type = "aerobic"
        reasons.append("HRV fuera de tu rango normal (media 7 días): hoy sin intensidad [Verificado]")
    if state.effort_mismatch and session_type in QUALITY:
        session_type = "aerobic"
        reasons.append("Esfuerzo percibido alto con FC baja en tu última sesión: posible fatiga, hoy sin intensidad [Verificado]")
    if state.sleep_score is not None and state.sleep_score < 50:
        # Mal sueño quita la calidad; sumado a HRV fuera de rango, el día pasa a recuperación (criterio Fase 3)
        if session_type in QUALITY or status == "outside_swc":
            session_type = DOWNGRADE[session_type]
        volume = 0.7
        reasons.append(f"Sueño {state.sleep_score:.0f}/100: -30 % de carga y sin calidad [Inferencia]")
    # Tras una parada se reconstruye la base antes de la intensidad [Práctica: Garmin, tipos de sesión sugerida;
    # umbral de 7 días: Inferencia]
    if state.days_since_activity is not None and state.days_since_activity >= 7 and session_type in QUALITY:
        session_type = "aerobic"
        reasons.append(f"{state.days_since_activity} días sin entrenar: primero base, la intensidad vuelve después [Práctica]")
    if state.consecutive_days >= 3 and session_type in QUALITY and tsb < -20:  # [Inferencia]
        session_type = "aerobic"
        reasons.append(f"{state.consecutive_days} días seguidos entrenando con TSB {tsb:.1f}: sin calidad hoy [Inferencia]")
    return session_type, volume, reasons


# ---------------------------------------------------------------- 12 reparto de la carga (como el "load focus" de Garmin)

# Fracción objetivo de la carga de 28 días: aeróbico bajo (Z1-Z2), aeróbico alto (Z3-Z5a) y anaeróbico (Z5b+).
# Garmin/Firstbeat no publican sus rangos: estos salen del modelo polarizado de §8.4 (80/20) [Inferencia]
FOCUS_TARGET = {"base_early": {"low": 0.80, "high": 0.15, "anaerobic": 0.05},
                "base": {"low": 0.80, "high": 0.15, "anaerobic": 0.05},
                "build": {"low": 0.75, "high": 0.20, "anaerobic": 0.05},
                "peak": {"low": 0.75, "high": 0.20, "anaerobic": 0.05}}
FOCUS_MARGIN = 0.05  # déficit mínimo para cambiar la sesión de la plantilla [Inferencia]


def load_focus(activities: list[dict]) -> dict[str, float] | None:
    """Reparto de la carga (icu_training_load) según el tiempo en las 7 zonas de FC de intervals.icu.

    Garmin necesita 7 días de datos para su load focus; aquí, cualquier carga > 0 en la ventana que se pase.
    """
    totals = {"low": 0.0, "high": 0.0, "anaerobic": 0.0}
    for a in activities:
        zones, load = a.get("icu_hr_zone_times") or [], a.get("icu_training_load") or 0
        time = sum(zones)
        if time and load:
            totals["low"] += load * sum(zones[:2]) / time
            totals["high"] += load * sum(zones[2:5]) / time
            totals["anaerobic"] += load * sum(zones[5:]) / time
    total = sum(totals.values())
    return {k: v / total for k, v in totals.items()} if total else None


def focus_kind(kind: str, phase: str, focus: dict[str, float] | None, goal: Goal | None) -> tuple[str, str | None]:
    """La sesión de calidad de la plantilla cubre el tipo de carga que más falta (como las sugerencias de Garmin)."""
    if focus is None or phase not in FOCUS_TARGET or kind not in ("vo2max", "threshold", "neuromuscular"):
        return kind, None
    target = FOCUS_TARGET[phase]
    gaps = {k: target[k] - focus[k] for k in target}
    worst = max(gaps, key=gaps.get)
    if gaps[worst] < FOCUS_MARGIN:
        return kind, None
    share = f"{focus[worst]:.0%} de tu carga frente al {target[worst]:.0%} objetivo"
    if worst == "low":
        return "aerobic", f"Falta carga aeróbica baja ({share}): hoy base [Inferencia]"
    if worst == "anaerobic":
        return "anaerobic", f"Falta carga anaeróbica ({share}) [Inferencia]"
    if run_class(goal) in ("half", "marathon") and phase in ("build", "peak"):
        return "tempo", f"Falta carga aeróbica alta ({share}): tempo a ritmo de maratón [Inferencia]"
    return ("vo2max" if kind == "neuromuscular" else kind), f"Falta carga aeróbica alta ({share}) [Inferencia]"


# ---------------------------------------------------------------- 13 ritmos a partir de la FC (como Garmin)

def hr_pace_model(samples: list[tuple[float, float, float]]) -> tuple[float, float] | None:
    """Recta velocidad (m/s) = a + b·FC con muestras (FC, velocidad, pendiente %) segundo a segundo.

    Solo llano (|pendiente| <= 2 %) y corriendo (> 1,5 m/s); mediana por tramos de 5 ppm con >= 60 s,
    para que pesen igual todas las intensidades. Hace falta >= 4 tramos y 15 ppm de rango. [Inferencia]
    """
    bins: dict[int, list[float]] = {}
    for hr, v, grade in samples:
        if hr and v and v > 1.5 and grade is not None and abs(grade) <= 2:
            bins.setdefault(int(hr) // 5 * 5, []).append(v)
    points = [(h + 2.5, median(v)) for h, v in bins.items() if len(v) >= 60]
    if len(points) < 4 or max(p[0] for p in points) - min(p[0] for p in points) < 15:
        return None
    b, a = linear_regression([p[0] for p in points], [p[1] for p in points])
    return (a, b) if b > 0 else None


def pace_at(bpm: float, model: tuple[float, float]) -> float:
    """Ritmo (seg/km) que tus carreras predicen para esa FC."""
    a, b = model
    return 1000 / max(a + b * bpm, 1.5)


def hr_bounds(zone: str, sport: str, lthr: float, max_hr: float | None, hr_cap: int | None) -> tuple[float, float] | None:
    """Límites de FC (ppm) de una zona de TrainSync; None en Z6 (abierta: manda el RPE)."""
    lo, hi = HR_ZONES["running" if sport == "running" else "cycling"][zone]
    if hi is None:
        return None
    lo_bpm, hi_bpm = lthr * lo, lthr * hi
    if zone == "Z1":
        lo_bpm = 0.5 * max_hr if max_hr else 0.65 * lthr  # abierta por abajo: límite holgado [Inferencia]
        hi_bpm = min(hi_bpm, hr_cap) if hr_cap else hi_bpm
    if zone == "Z2" and hr_cap:
        # El techo LTHR − 30 manda en sesiones fáciles; 5 % de margen si queda por debajo de la Z2
        hi_bpm, lo_bpm = hr_cap, min(lo_bpm, hr_cap - 0.05 * lthr)
    return lo_bpm, hi_bpm


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
    run_pace: float | None = None  # ritmo umbral de carrera, seg/km (intervals.icu threshold_pace)
    ftp: float | None = None  # vatios; sin FTP la bici se prescribe por FC
    run_model: tuple[float, float] | None = None  # velocidad (m/s) = a + b·FC, de tus carreras (hr_pace_model)
    day_minutes: dict[str, int] | None = None  # minutos disponibles por día (perfil del usuario); None = sin límite
    plan_start: date | None = None
    warnings: list[str] = field(default_factory=list)
    needs_test: set[str] = field(default_factory=set)  # deportes sin LTHR/CSS fiable (onboarding RF-03)

    def lthr_for(self, sport: str) -> float | None:
        return self.lthr_by_sport.get(sport) or self.lthr


# Ratio de intensidad medio por zona para estimar TSS planificado. [Inferencia]
ZONE_IF = {"Z1": 0.65, "Z2": 0.75, "Z3": 0.88, "Z4": 0.97, "Z5": 1.05, "Z6": 1.15}
NAMES = {"recovery": "Recuperación", "aerobic": "Aeróbico base", "long": "Tirada larga",
         "vo2max": "Intervalos VO2max", "threshold": "Umbral", "race_specific": "Específico de carrera",
         "rest": "Descanso", "strength": "Fuerza", "neuromuscular": "Neuromuscular", "brick": "Transición bici-carrera (brick)",
         "test": "Test de campo", "tempo": "Tempo", "anaerobic": "Anaeróbico"}
GOALS = {"recovery": "Recuperar sin generar fatiga", "aerobic": "Base aeróbica y eficiencia (Z2 pura, sin zona gris)",
         "long": "Resistencia aeróbica y economía", "vo2max": "Elevar el techo aeróbico",
         "threshold": "Subir el umbral de lactato", "race_specific": "Ritmo y sensaciones de competición",
         "neuromuscular": "Potencia y economía sin fatiga (esfuerzos muy cortos, recuperación completa)",
         "brick": "Acostumbrar las piernas a correr tras la bici", "test": "Medir LTHR o CSS para fijar tus zonas",
         "tempo": "Ritmo de maratón sostenido (aeróbico alto)",
         "anaerobic": "Capacidad anaeróbica: esfuerzos de 40–60″ por encima del VO2max"}


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
    if sport == "running" and profile.run_model and lthr and kind != "test" and not entry["id"].startswith("T-"):
        # Como Garmin: ritmos calculados desde tus zonas de FC con tu propia relación FC-velocidad (§13)
        fmt = lambda sec: f"{int(sec // 60)}:{round(sec % 60):02d}"
        bounds = {z: hr_bounds(z, sport, lthr, profile.max_hr, hr_cap) for z in targets}
        targets = {z: (f"> {fmt(pace_at(b[1], profile.run_model))} /km" if z == "Z1"  # abierta: más lento que
                       else f"{fmt(pace_at(b[0], profile.run_model))}-{fmt(pace_at(b[1], profile.run_model))} /km")
                   if b else targets[z] for z, b in bounds.items()}
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
    # Sin carrera objetivo (o con un deporte que no practica), rotan los deportes elegidos día a día [Inferencia]
    return profile.active_sports[WEEKDAYS.index(weekday) % len(profile.active_sports)]


def propose_day(day: date, profile: Profile, goal: Goal | None, state: DayState | None = None,
                scale: float = 1.0) -> dict:
    """Propuesta diaria (RF-05): sesión principal + alternativas por deporte activo + fuerza."""
    weekday = WEEKDAYS[day.weekday()]
    week = day.isocalendar().week
    phase, recovery_week = phase_for(day, goal, profile.plan_start)
    template, moved = fit_days(template_for(phase, goal), profile.rest_days)
    kind = template.get(weekday, "rest")
    volume, reasons, why = PHASE_VOLUME[phase] * scale, [], []
    if weekday in moved:
        reasons.append(f"Viene del {DAY_NAMES[moved[weekday]]}: ese día no entrenas [Inferencia]")
    if recovery_week:
        volume *= 0.7
        if kind in QUALITY and weekday == "THU":
            kind = "aerobic"
        reasons.append("Semana de descarga (patrón 3+1): -30 % de volumen [Práctica]")
    if state is not None:
        kind, focus_reason = focus_kind(kind, phase, state.load_focus, goal)
        reasons += [focus_reason] if focus_reason else []
        kind, factor, why = adjust(kind, state)
        volume *= factor
        reasons += why

    out = {"date": day.isoformat(), "weekday": weekday, "phase": phase, "recovery_week": recovery_week,
           "reasons": reasons, "main": None, "alternatives": [], "strength": None}
    if kind == "rest":
        return out
    preferred = "cycling" if kind == "brick" else main_sport(weekday, goal, profile)
    event = "trail" if goal and goal.trail else None
    # Sin fuerza si el estado del día ha obligado a bajar la carga
    if profile.strength != "none" and weekday in STRENGTH_DAYS[phase] and not why:
        out["strength"] = build_strength(goal.sport if goal else preferred, profile, phase)
        day_cap = profile.day_minutes.get(weekday) if profile.day_minutes else None
        if day_cap and out["strength"]["duration_min"] + 20 > day_cap:  # fuerza + 20′ de sesión principal no caben
            out["strength"] = None
            reasons.append(f"Sin fuerza hoy: no cabe en tus {day_cap} min junto a la sesión principal [Inferencia]")
    # Minutos del día que le quedan a la sesión principal (descontada la fuerza): la sesión tiene que caber
    cap = profile.day_minutes.get(weekday) if profile.day_minutes else None
    if cap:
        cap = max(cap - (out["strength"]["duration_min"] if out["strength"] else 0), 20)

    def build(sport: str, vol: float) -> dict | None:
        session = build_session(kind, sport, profile, vol, phase, week, event, long_cap(goal, sport))
        for _ in range(3):  # la duración no es lineal con el volumen (bloques fijos): se ajusta
            if not session or not cap or session["duration_min"] <= cap:
                break
            vol *= cap / session["duration_min"]
            session = build_session(kind, sport, profile, vol, phase, week, event, long_cap(goal, sport))
        return session

    sessions = {s: build(s, volume) for s in [preferred] + [s for s in profile.active_sports if s != preferred]}
    sessions = {s: v for s, v in sessions.items() if v}
    if not sessions:
        return out
    sport, out["main"] = next(iter(sessions.items()))
    target = out["main"]["tss"]
    for other, alt in list(sessions.items())[1:]:
        # Alternativa equivalente en carga (±15 %): el TSS no es lineal con la duración, se itera
        alt_volume = volume
        for _ in range(3):
            if not alt["tss"] or abs(alt["tss"] - target) <= 0.15 * target or (cap and alt["duration_min"] >= cap):
                break
            alt_volume *= target / alt["tss"]
            alt = build(other, alt_volume)
        out["alternatives"].append(alt)
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
           "aerobic": 11, "neuromuscular": 11, "recovery": 5, "tempo": 15, "anaerobic": 11}


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
        test = build_session("test", sport, profile)
        if test is None:  # deporte sin test en el catálogo (aguas abiertas)
            continue
        # Solo en un día de calidad donde el test quepa en los minutos disponibles (descontada la fuerza)
        fits = lambda d: not profile.day_minutes or test["duration_min"] + (
            d["strength"]["duration_min"] if d["strength"] else 0) <= profile.day_minutes.get(d["weekday"], 0)
        quality = [d for d in days if d["main"] and d["main"]["type"] in QUALITY and d["date"] not in used and fits(d)]
        day = next((d for d in quality if d["main"]["sport"] == sport), quality[0] if quality else None)
        test = day and build_session("test", sport, profile, phase=day["phase"])
        if test:
            day["alternatives"].insert(0, day["main"])
            day["main"] = test
            day["reasons"].append(f"Test de {sport}: faltan datos fisiológicos fiables para fijar tus zonas [Práctica]")
            used.add(day["date"])
