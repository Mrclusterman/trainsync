"""Sesión de TrainSync → texto de entrenamiento de intervals.icu, que lo convierte en pasos para el reloj.

Sintaxis (forum.intervals.icu/t/workout-builder/1163): pasos con "- ", duración "10m"/"30s"/"1m30",
"80-90% LTHR", "Z2 HR", repeticiones con una línea "6x" antes de los pasos; ritmo absoluto "2:05-1:55/100m Pace"
(forum.intervals.icu/t/specify-workouts-using-absolute-pace/115846). El texto anterior a la duración es el aviso del paso.
"""
from app.services.training_engine import HR_ZONES, RPE_BY_ZONE, SWIM, SWIM_ZONES

SPORT_TYPES = {"running": "Run", "cycling": "Ride", "swimming_pool": "Swim", "swimming_open": "Swim"}


def duration(minutes: float) -> str:
    s = round(minutes * 60)
    if s < 60:
        return f"{s}s"
    return f"{s // 60}m" + (f"{s % 60}" if s % 60 else "")


def pace(sec_per_100m: float) -> str:
    return f"{int(sec_per_100m // 60)}:{round(sec_per_100m % 60):02d}"


def target(zone: str, sport: str, lthr: float | None, css: float | None, hr_cap: int | None) -> str:
    if sport in SWIM:
        lo, hi = SWIM_ZONES[zone]
        if not css or lo is None or hi is None:  # sin CSS o zona abierta: se nada por sensación
            return f"RPE {RPE_BY_ZONE[zone]}"
        return f"{pace(css * hi)}-{pace(css * lo)}/100m Pace"  # más lento primero, como en la documentación
    if not lthr or zone == "Z6":  # en esfuerzos máximos cortos la FC no da tiempo a subir: manda el RPE
        return f"RPE {RPE_BY_ZONE[zone]}"
    if zone == "Z1":
        return "Z1 HR"  # zona abierta por abajo: se usan las zonas de FC del propio intervals.icu
    lo, hi = HR_ZONES["running" if sport == "running" else "cycling"][zone]
    if zone == "Z2" and hr_cap:
        # El techo LTHR − 30 manda en sesiones fáciles (igual que en build_session); 5 % de margen si queda por debajo de Z2
        hi = hr_cap / lthr
        lo = min(lo, hi - 0.05)
    return f"{round(lo * 100)}-{round(hi * 100)}% LTHR"


def describe(session: dict, lthr: float | None, css: float | None) -> str:
    """Texto de la sesión: objetivo, notas de cada bloque y los pasos."""
    sport, cap = session["sport"], session.get("hr_cap")
    if session["type"] == "test":  # como en build_session: un objetivo con la LTHR o el CSS que se mide sesgaría el test
        lthr = css = None
    lines = [session["goal"]]
    # Las notas van en líneas sin guion: así su texto ("6×20″", "15 %") no se interpreta como duración u objetivo
    lines += [f"{b['block'].capitalize()}: {b['detail']}" for b in session["blocks"] if b.get("detail")]
    def step(name: str, minutes: float, zone: str) -> str:
        goal = target(zone, sport, lthr, css, cap)
        # intervals.icu descarta "RPE x" detrás de la duración (comprobado 2026-10-20): delante pasa al aviso del paso
        if goal.startswith("RPE"):
            return f"- {name} {goal} {duration(minutes)}"
        return f"- {name} {duration(minutes)} {goal}"

    for b in session["blocks"]:
        if b["reps"] > 1:
            lines += ["", f"{b['reps']}x", step(b["block"], b["minutes"], b["zone"])]
            if b.get("rest_minutes"):
                lines.append(step("recuperación", b["rest_minutes"], b.get("rest_zone", "Z1")))
            lines.append("")
        else:
            lines.append(step(b["block"], b["minutes"], b["zone"]))
    return "\n".join(lines).replace("\n\n\n", "\n\n").strip() + "\n"


def event(session: dict, day: str, lthr: float | None, css: float | None) -> dict:
    """Evento para POST /events/bulk?upsert=true: un external_id por día, así reenviar lo actualiza.

    intervals.icu ignora un uid propio y asigna el suyo (comprobado 2026-10-20); external_id sí se respeta.
    """
    return {"category": "WORKOUT", "start_date_local": f"{day}T00:00:00", "type": SPORT_TYPES[session["sport"]],
            "name": f"TrainSync · {session['name']}", "description": describe(session, lthr, css),
            "moving_time": session["duration_min"] * 60, "external_id": f"trainsync-{day}"}
