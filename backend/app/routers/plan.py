"""Endpoints del plan: estado del atleta, propuesta de hoy y plan de N días."""
from datetime import date, timedelta
from functools import lru_cache

from fastapi import APIRouter, HTTPException, Query

from app import config, schemas
from app.services import training_engine as engine
from app.services.intervals_client import IntervalsClient

router = APIRouter()

SPORT_TYPES = {"Run": "running", "TrailRun": "running", "VirtualRun": "running",
               "Ride": "cycling", "VirtualRide": "cycling", "GravelRide": "cycling", "MountainBikeRide": "cycling",
               "Swim": "swimming_pool", "OpenWaterSwim": "swimming_open", "WeightTraining": "strength"}


@lru_cache
def client() -> IntervalsClient:
    if not (config.INTERVALS_ATHLETE_ID and config.INTERVALS_API_KEY):
        raise HTTPException(503, "Faltan INTERVALS_ATHLETE_ID / INTERVALS_API_KEY en .env")
    return IntervalsClient(config.INTERVALS_ATHLETE_ID, config.INTERVALS_API_KEY)


def goal_from_races(races: list[dict], today: date) -> engine.Goal | None:
    """Objetivo prioritario: la carrera futura de mayor prioridad (A > B > C) y más cercana."""
    upcoming = [r for r in races if r.get("start_date_local", "")[:10] >= today.isoformat()]
    if not upcoming:
        return None
    r = min(upcoming, key=lambda r: (r.get("category", "RACE_C"), r["start_date_local"]))
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
    return engine.DayState(
        ctl=last["ctl"], atl=last["atl"],
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


def load_context(today: date):
    c = client()
    profile = fill_physiology(config.load_profile(), c.athlete())
    wellness = c.wellness(today - timedelta(35), today)  # 7 d + 28 d de línea base HRV
    activities = c.activities(today - timedelta(7), today)
    goal = goal_from_races(c.races(today, today + timedelta(days=365)), today)
    return profile, goal, day_state(wellness, activities, today), activities


def load_warnings(srpe: dict) -> list[str]:
    # [Práctica] monotonía > 2 se asocia a más riesgo; Foster 1998 usa umbrales individuales
    if srpe["monotony"] and srpe["monotony"] > 2:
        return [f"Monotonía de carga {srpe['monotony']} (> 2) esta semana: alterna días duros y fáciles"]
    return []


@router.get("/state", response_model=schemas.State)
def state():
    today = date.today()
    profile, goal, s, activities = load_context(today)
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
def plan_today():
    today = date.today()
    profile, goal, s, _ = load_context(today)
    return engine.plan(today, 1, profile, goal, s)[0]


@router.get("/plan", response_model=schemas.Plan)
def plan(days: int = Query(14, ge=1, le=42)):
    today = date.today()
    profile, goal, s, _ = load_context(today)
    return {"goal": goal, "days": engine.plan(today, days, profile, goal, s)}
