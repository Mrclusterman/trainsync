"""Endpoints del plan de carrera: estado del atleta, propuesta de hoy y plan de N días.

Cada usuario con sesión usa su propia cuenta de intervals.icu vinculada (app/links.py), nunca la del servidor, y sus
preferencias del perfil de Salud: deportes, días y minutos disponibles y fuerza. La fisiología sale de intervals.icu.
"""
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import auth, db, schemas
from app.links import user_client
from app.services import training_engine as engine, workout_text
from app.services.intervals_client import IntervalsClient

router = APIRouter()

SPORT_TYPES = {"Run": "running", "TrailRun": "running", "VirtualRun": "running",
               "Ride": "cycling", "VirtualRide": "cycling", "GravelRide": "cycling", "MountainBikeRide": "cycling",
               "Swim": "swimming_pool", "OpenWaterSwim": "swimming_open", "WeightTraining": "strength"}


def pick_goal(goals: list[engine.Goal], today: date) -> engine.Goal | None:
    """Objetivo prioritario: la carrera futura de mayor prioridad (A > B > C) y, a igual prioridad, la más cercana."""
    upcoming = [g for g in goals if g.date >= today]
    return min(upcoming, key=lambda g: (g.priority, g.date)) if upcoming else None


def race_to_goal(r: dict) -> engine.Goal:
    """Carrera del calendario de intervals.icu (RACE_A/B/C) como objetivo."""
    name = r.get("name") or "Objetivo"
    lowered = name.lower()
    # [Inferencia] intervals.icu no tiene tipo triatlón/duatlón: se deduce del nombre
    sport = ("triathlon" if "triat" in lowered else "duathlon" if "duat" in lowered
             else SPORT_TYPES.get(r.get("type", ""), "running"))
    return engine.Goal(name=name, sport=sport, date=date.fromisoformat(r["start_date_local"][:10]),
                       priority=r.get("category", "RACE_A")[-1],
                       distance_km=r["distance"] / 1000 if r.get("distance") else None,
                       trail=r.get("type") == "TrailRun" or "trail" in lowered)


SETTING_SPORTS = {"Run": "running", "Ride": "cycling", "Swim": "swimming_pool"}


def fill_physiology(profile: engine.Profile, athlete: dict) -> engine.Profile:
    """Rellena LTHR por deporte / FC máx / FC reposo desde intervals.icu si no vienen en .env."""
    config_lthr = profile.lthr
    for s in athlete.get("sportSettings") or []:
        sport = next((SETTING_SPORTS[t] for t in s.get("types") or [] if t in SETTING_SPORTS), None)
        lthr, max_hr = s.get("lthr"), s.get("max_hr")
        if not sport or not lthr:
            continue
        profile.max_hr = profile.max_hr or max_hr
        if config_lthr:  # LTHR de .env manda sobre intervals.icu
            continue
        # [Inferencia] LTHR >= 97 % de la FC máx no es plausible: suele ser un ajuste antiguo o mal configurado
        if max_hr and lthr >= 0.97 * max_hr:
            profile.warnings.append(f"LTHR de {sport} en intervals.icu ({lthr}) no es plausible con FC máx {max_hr}: "
                                    "se usa la de otro deporte. Haz el test de LTHR (R-TEST-LTHR) y corrígela en intervals.icu.")
            continue
        profile.lthr_by_sport.setdefault(sport, lthr)
    profile.lthr = profile.lthr or profile.lthr_by_sport.get("cycling") or next(iter(profile.lthr_by_sport.values()), None)
    profile.rest_hr = profile.rest_hr or athlete.get("icu_resting_hr")
    # Umbrales de ritmo y potencia de intervals.icu (threshold_pace en m/s) para los objetivos que llegan al reloj
    for s in athlete.get("sportSettings") or []:
        types, pace = s.get("types") or [], s.get("threshold_pace")
        if "Swim" in types and pace and not profile.css:
            profile.css = 100 / pace
        if "Run" in types and pace:
            profile.run_pace = 1000 / pace
        if "Ride" in types and s.get("ftp"):
            profile.ftp = s["ftp"]
    # Onboarding (RF-03): test en los deportes sin LTHR propia o sin CSS
    for sport in ("running", "cycling"):
        if sport in profile.active_sports and sport not in profile.lthr_by_sport and not config_lthr:
            profile.needs_test.add(sport)
    if "swimming_pool" in profile.active_sports and not profile.css:
        profile.needs_test.add("swimming_pool")
    return profile


def day_state(wellness: list[dict], activities: list[dict], today: date) -> engine.DayState:
    rows = sorted((w for w in wellness if w.get("ctl") is not None), key=lambda w: w["id"])
    if not rows:
        raise HTTPException(502, "intervals.icu no devolvió wellness con CTL/ATL")
    last = rows[-1]
    trained = {a["start_date_local"][:10] for a in activities if a.get("start_date_local")}
    streak = 0
    while (today - timedelta(streak + 1)).isoformat() in trained:
        streak += 1
    by_day = {w["id"]: w.get("hrv") for w in wellness}
    recent = [a for a in activities if a.get("start_date_local", "")[:10] >= (today - timedelta(2)).isoformat()]
    days_ago = [(today - date.fromisoformat(d)).days for d in trained if d <= today.isoformat()]
    month = [a for a in activities if a.get("start_date_local", "")[:10] > (today - timedelta(28)).isoformat()]
    return engine.DayState(
        ctl=last["ctl"], atl=last["atl"],
        load_focus=engine.load_focus(month), days_since_activity=min(days_ago) if days_ago else None,
        effort_mismatch=any(engine.effort_mismatch(a.get("icu_rpe"), a.get("icu_hr_zone_times")) for a in recent),
        hrv_history=[by_day.get((today - timedelta(d)).isoformat()) for d in range(34, -1, -1)],
        sleep_score=last.get("sleepScore") if last["id"] == today.isoformat() else None,
        consecutive_days=streak,
    )


def last_activity(activities: list[dict]) -> dict | None:
    done = [a for a in activities if a.get("start_date_local")]
    if not done:
        return None
    a = max(done, key=lambda a: a["start_date_local"])
    zones = a.get("icu_hr_zone_times") or []
    return {"name": a.get("name"), "date": a["start_date_local"][:10], "sport": SPORT_TYPES.get(a.get("type"), "other"),
            "duration_min": round((a.get("moving_time") or 0) / 60), "tss": a.get("icu_training_load"),
            "hr_zone_secs": zones, "gray_zone": engine.gray_zone(zones)}


_SAMPLES: dict[tuple[str, str], tuple] = {}  # (cuenta, actividad) → muestras: nunca se mezclan datos entre usuarios


def run_samples(c: IntervalsClient, activity_id: str) -> tuple:
    """(FC, velocidad, pendiente) segundo a segundo, sin los 5 primeros minutos (la FC va con retraso)."""
    key = (str(c.http.base_url), activity_id)
    if key not in _SAMPLES:
        if len(_SAMPLES) > 512:  # shortcut: caché en memoria del proceso; pasar a Redis con varios servidores
            _SAMPLES.clear()
        st = c.streams(activity_id)
        _SAMPLES[key] = tuple(zip(st.get("heartrate", []), st.get("velocity_smooth", []), st.get("grade_smooth", [])))[300:]
    return _SAMPLES[key]


RUNS_FOR_MODEL = 12  # carreras recientes para la relación FC-velocidad [Inferencia]


def fit_run_model(profile: engine.Profile, activities: list[dict], c: IntervalsClient) -> None:
    """Relación FC-velocidad de tus carreras (como Garmin: ritmos desde la FC) y aviso si choca con la LTHR."""
    runs = sorted((a for a in activities if a.get("type") == "Run" and a.get("average_heartrate")),
                  key=lambda a: a["start_date_local"])[-RUNS_FOR_MODEL:]
    if "running" not in profile.active_sports or not runs:
        return
    profile.run_model = engine.hr_pace_model([x for a in runs for x in run_samples(c, str(a["id"]))])
    lthr = profile.lthr_for("running")
    if profile.run_model and lthr and profile.run_pace:
        predicted = engine.pace_at(lthr, profile.run_model)
        # [Inferencia] más de un 5 % de diferencia: LTHR o ritmo umbral desajustados
        if abs(predicted / profile.run_pace - 1) > 0.05:
            fmt = lambda sec: f"{int(sec // 60)}:{round(sec % 60):02d}"
            profile.warnings.append(
                f"Con tu LTHR de carrera ({lthr:.0f}) tus carreras predicen {fmt(predicted)}/km en umbral, pero tu ritmo "
                f"umbral en intervals.icu es {fmt(profile.run_pace)}/km. Una de las dos está desajustada: haz el test "
                "de LTHR (R-TEST-LTHR).")


RACE_SPORTS = ("running", "cycling", "swimming_pool")  # caminar no existe en el motor de carrera


def own_goals(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)) -> list[engine.Goal]:
    """Carreras creadas en la app por el usuario."""
    rows = session.scalars(select(db.RaceGoal).where(db.RaceGoal.user_id == user.id)).all()
    return [engine.Goal(name=r.name, sport=r.sport, date=r.date, priority=r.priority, distance_km=r.distance_km,
                        trail=r.trail) for r in rows]


def race_base(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)) -> engine.Profile:
    """Preferencias del perfil de Salud del usuario; sin perfil, valores por defecto (nunca los de otra persona)."""
    row = session.get(db.HealthProfileRow, user.id)
    if row is None:
        return engine.Profile(active_sports=["running", "cycling"])
    plan = row.plan
    sports = [s for s in plan.get("cardio", []) if s in RACE_SPORTS]
    if not sports:
        raise HTTPException(409, "El plan de carrera necesita correr, bici o natación entre tus actividades del perfil")
    days = {d: m for d, m in plan["days"].items() if m}
    return engine.Profile(active_sports=sports, strength=plan.get("strength", "gym"),
                          rest_days=[d for d in engine.WEEKDAYS if d not in days], day_minutes=days)


def load_context(c: IntervalsClient, today: date, base: engine.Profile, goals: list[engine.Goal] | None = None):
    profile = fill_physiology(base, c.athlete())
    wellness = c.wellness(today - timedelta(35), today)  # 7 d + 28 d de línea base HRV
    activities = c.activities(today - timedelta(365), today)  # 28 d de reparto de carga; carreras para el modelo
    fit_run_model(profile, activities, c)
    # Objetivo: entre las carreras creadas en la app y las del calendario de intervals.icu; las copias que la app sube a
    # intervals.icu (external_id trainsync-goal-…) se ignoran, porque esas carreras ya llegan desde la app
    races = [r for r in c.races(today, today + timedelta(days=365))
             if not (r.get("external_id") or "").startswith("trainsync-goal-")]
    goal = pick_goal([race_to_goal(r) for r in races] + (goals or []), today)
    week = [a for a in activities if a.get("start_date_local", "")[:10] >= (today - timedelta(7)).isoformat()]
    return profile, goal, day_state(wellness, activities, today), week


def load_warnings(srpe: dict) -> list[str]:
    # [Práctica] monotonía > 2 se asocia a más riesgo; Foster 1998 usa umbrales individuales
    if srpe["monotony"] and srpe["monotony"] > 2:
        return [f"Monotonía de carga {srpe['monotony']} (> 2) esta semana: alterna días duros y fáciles"]
    return []


@router.get("/state", response_model=schemas.State)
def state(c: IntervalsClient = Depends(user_client), base: engine.Profile = Depends(race_base),
         goals: list[engine.Goal] = Depends(own_goals)):
    today = date.today()
    profile, goal, s, activities = load_context(c, today, base, goals)
    phase, recovery_week = engine.phase_for(today, goal, profile.plan_start)
    daily = [sum(a.get("session_rpe") or 0 for a in activities if a.get("start_date_local", "")[:10] == (today - timedelta(d)).isoformat())
             for d in range(6, -1, -1)]
    load, mono, strain = engine.monotony(daily)
    srpe = {"load": load, "monotony": mono, "strain": strain}
    return {"date": today.isoformat(), "ctl": round(s.ctl, 1), "atl": round(s.atl, 1), "tsb": round(s.tsb, 1),
            "hrv": s.hrv_history[-1], "hrv_status": engine.hrv_status(s.hrv_history), "sleep_score": s.sleep_score,
            "consecutive_days": s.consecutive_days, "goal": goal, "phase": phase, "recovery_week": recovery_week,
            "physiology": {"lthr": profile.lthr, "lthr_by_sport": profile.lthr_by_sport, "max_hr": profile.max_hr,
                           "rest_hr": profile.rest_hr, "css": profile.css},
            "warnings": profile.warnings + load_warnings(srpe),
            "srpe_week": srpe, "needs_test": sorted(profile.needs_test),
            "last_activity": last_activity(activities)}


@router.get("/plan/today", response_model=schemas.Day)
def plan_today(c: IntervalsClient = Depends(user_client), base: engine.Profile = Depends(race_base),
         goals: list[engine.Goal] = Depends(own_goals)):
    today = date.today()
    profile, goal, s, _ = load_context(c, today, base, goals)
    return engine.plan(today, 1, profile, goal, s)[0]


@router.get("/plan", response_model=schemas.Plan)
def plan(days: int = Query(14, ge=1, le=42), c: IntervalsClient = Depends(user_client),
         base: engine.Profile = Depends(race_base),
         goals: list[engine.Goal] = Depends(own_goals)):
    today = date.today()
    profile, goal, s, _ = load_context(c, today, base, goals)
    return {"goal": goal, "days": engine.plan(today, days, profile, goal, s)}


@router.post("/plan/today/send", response_model=schemas.Sent)
def send_today(c: IntervalsClient = Depends(user_client), base: engine.Profile = Depends(race_base),
         goals: list[engine.Goal] = Depends(own_goals)):
    """Envía la sesión principal de hoy al calendario de intervals.icu (reenviar la sustituye, no la duplica)."""
    today = date.today()
    profile, goal, s, _ = load_context(c, today, base, goals)
    main = engine.plan(today, 1, profile, goal, s)[0]["main"]
    if main is None:
        raise HTTPException(409, "Hoy toca descanso: no hay sesión que enviar")
    event = workout_text.event(main, today.isoformat(), profile)
    created = c.upsert_workouts([event])[0]
    return {"id": created["id"], "date": today, "name": event["name"], "description": event["description"]}
