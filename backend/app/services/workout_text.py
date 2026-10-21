"""Sesión de TrainSync → entrenamiento estructurado de intervals.icu, que lo pasa al reloj o al ciclocomputador.

Cada paso lleva un objetivo que intervals.icu convierte en límites (el reloj avisa al salirse del rango).
Sintaxis (forum.intervals.icu/t/workout-builder/1163): pasos con "- ", duración "10m"/"30s"/"1m30",
"72.5-80.2% HR" (de la FC máx.), "56-75%" (del FTP), repeticiones con una línea "6x" antes de los pasos;
ritmo absoluto "4:50-4:40/km Pace" o "/100m Pace" (forum.intervals.icu/t/specify-workouts-using-absolute-pace/115846).
El texto anterior a la duración es el aviso del paso. Formatos comprobados contra la API el 2026-10-21.

Una sola métrica por sesión: según usuarios del foro, al reloj solo llega la métrica principal y mezclar ritmo y FC
pierde la segunda (forum.intervals.icu/t/run-intervals-using-pace-pace-not-synced-to-garmin-connect/118130).
"""
from app.services.training_engine import RPE_BY_ZONE, SWIM, SWIM_ZONES, Profile, hr_bounds, pace_at

SPORT_TYPES = {"running": "Run", "cycling": "Ride", "swimming_pool": "Swim", "swimming_open": "Swim", "walking": "Walk"}
# Running por ritmo sin modelo FC-velocidad: % del ritmo umbral como tiempo por km (más % = más lento). Z5 = 5a + 5b.
# [Práctica: Friel, "Quick guide to setting zones", TrainingPeaks]; el límite lento de Z1 es [Inferencia]
RUN_PACE_ZONES = {"Z1": (1.29, 1.60), "Z2": (1.14, 1.29), "Z3": (1.06, 1.13), "Z4": (0.99, 1.05), "Z5": (0.90, 1.00)}
# Potencia en % del FTP (Coggan, MEMORIA §8.3); el límite bajo de Z1 es [Inferencia]
POWER_ZONES = {"Z1": (40, 55), "Z2": (56, 75), "Z3": (76, 90), "Z4": (91, 105), "Z5": (106, 120), "Z6": (121, 150)}
PACE_FIRST = ("vo2max", "threshold", "race_specific", "tempo")  # sin modelo: solo calidad por ritmo umbral [Práctica]


def duration(minutes: float) -> str:
    s = round(minutes * 60)
    if s >= 120:  # el bloque de relleno sale con segundos sueltos (30′01″): a 5 s se lee mejor en el reloj
        s = round(s / 5) * 5
    if s < 60:
        return f"{s}s"
    return f"{s // 60}m" + (f"{s % 60}" if s % 60 else "")


def pace(seconds: float) -> str:
    return f"{int(seconds // 60)}:{round(seconds % 60):02d}"


def metric(session: dict, profile: Profile) -> str:
    """La métrica de toda la sesión: pace, power, hr o rpe (sin objetivo en el reloj)."""
    sport, kind = session["sport"], session["type"]
    if kind == "test":  # como en build_session: un objetivo con el umbral que se mide sesgaría el test
        return "rpe"
    if sport in SWIM:
        return "pace" if profile.css else "rpe"
    if sport == "cycling" and profile.ftp:
        return "power"
    if sport == "running" and not session["id"].startswith("T-"):  # en trail el ritmo no sirve: por FC
        # Como Garmin: con tu relación FC-velocidad, toda la carrera va por ritmo; sin ella, solo la calidad
        if profile.run_model and profile.lthr_for(sport) or profile.run_pace and kind in PACE_FIRST:
            return "pace"
    return "hr" if profile.lthr_for(sport) and profile.max_hr else "rpe"


def target(zone: str, session: dict, profile: Profile, mode: str) -> str | None:
    """Objetivo del paso en sintaxis de intervals.icu; None si se hace por sensación (Z6 o sin umbral)."""
    sport = session["sport"]
    if mode == "rpe" or zone == "Z6" and mode != "power":  # en esfuerzos máximos cortos manda el RPE
        return None
    if mode == "power":
        lo, hi = POWER_ZONES[zone]
        return f"{lo}-{hi}%"
    if mode == "pace" and sport in SWIM:
        fast, slow = SWIM_ZONES[zone]  # factores del CSS (seg/100m): (más rápido, más lento)
        slow = slow or fast + 0.15  # Z1 abierta: 15 % más lenta como límite [Inferencia]
        return f"{pace(profile.css * slow)}-{pace(profile.css * fast)}/100m Pace"  # más lento primero
    lthr = profile.lthr_for(sport)
    bounds = hr_bounds(zone, sport, lthr, profile.max_hr, session.get("hr_cap")) if lthr else None
    if mode == "pace" and profile.run_model and bounds:
        # Ritmos desde tus zonas de FC con tu relación FC-velocidad (training_engine §13)
        lo_bpm, hi_bpm = bounds
        return f"{pace(pace_at(lo_bpm, profile.run_model))}-{pace(pace_at(hi_bpm, profile.run_model))}/km Pace"
    if mode == "pace":
        fast, slow = RUN_PACE_ZONES[zone]
        return f"{pace(profile.run_pace * slow)}-{pace(profile.run_pace * fast)}/km Pace"
    # FC: pulsaciones de las zonas de TrainSync expresadas en % de la FC máx., porque intervals.icu no acepta ppm
    # y "% LTHR" usaría la LTHR guardada en intervals.icu, que puede no ser la que usa TrainSync
    lo_bpm, hi_bpm = bounds
    pct = lambda bpm: f"{100 * bpm / profile.max_hr:.1f}".rstrip("0").rstrip(".")
    return f"{pct(lo_bpm)}-{pct(hi_bpm)}% HR"


def describe(session: dict, profile: Profile) -> str:
    """Texto de la sesión: objetivo, notas de cada bloque y los pasos."""
    mode = metric(session, profile)
    lines = [session["goal"]]
    # Las notas van en líneas sin guion: así su texto ("6×20″", "15 %") no se interpreta como duración u objetivo
    lines += [f"{b['block'].capitalize()}: {b['detail']}" for b in session["blocks"] if b.get("detail")]

    def step(name: str, minutes: float, zone: str) -> str:
        goal = target(zone, session, profile, mode)
        if goal is None:
            # intervals.icu descarta "RPE x" detrás de la duración (comprobado 2026-10-20): delante pasa al aviso del paso
            return f"- {name} RPE {RPE_BY_ZONE[zone]} {duration(minutes)}"
        return f"- {name} {duration(minutes)} {goal}"

    for b in session["blocks"]:
        if b["reps"] > 1:
            lines += ["", f"{b['reps']}x", step(b["block"], b["minutes"], b["zone"])]
            if b.get("rest_minutes") and session["sport"] in SWIM:
                lines.append(f"- descanso {duration(b['rest_minutes'])}")  # en la piscina se descansa parado: sin objetivo
            elif b.get("rest_minutes"):
                lines.append(step("recuperación", b["rest_minutes"], b.get("rest_zone", "Z1")))
            lines.append("")
        else:
            lines.append(step(b["block"], b["minutes"], b["zone"]))
    return "\n".join(lines).replace("\n\n\n", "\n\n").strip() + "\n"


def event(session: dict, day: str, profile: Profile) -> dict:
    """Evento para POST /events/bulk?upsert=true: un external_id por día, así reenviar lo actualiza.

    intervals.icu ignora un uid propio y asigna el suyo (comprobado 2026-10-20); external_id sí se respeta.
    """
    return {"category": "WORKOUT", "start_date_local": f"{day}T00:00:00", "type": SPORT_TYPES[session["sport"]],
            "name": f"TrainSync · {session['name']}", "description": describe(session, profile),
            "moving_time": session["duration_min"] * 60, "external_id": f"trainsync-{day}"}
