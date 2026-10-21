"""Planes de salud sin fecha (docs/proyecto/DISENO.md). La validación completa con simulación es la fase D."""
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import health_plan as hp
from app.services.training_engine import WEEKDAYS

MONDAY = date(2026, 10, 26)
ALL_DAYS = {d: 60 for d in WEEKDAYS}


def profile(**kw) -> hp.HealthProfile:
    base = {"goal": "fitness", "days": {"MON": 60, "WED": 60, "FRI": 60, "SAT": 90}, "cardio": ["walking"]}
    return hp.HealthProfile(**(base | kw))


def sessions(week: dict, sport: str | None = None) -> list[dict]:
    return [s for d in week["days"] for s in d["sessions"] if sport is None or s["sport"] == sport]


# ---- §3–§4 dosis y bloques

def test_dose_starts_low_and_grows_within_the_block():
    p = profile(level="beginner")
    weeks = [hp.week_dose(p, [], w) for w in range(4)]
    assert weeks[0]["aerobic"] == 180  # 60 % de 300 (fitness)
    assert weeks[1]["aerobic"] == 198 and weeks[2]["aerobic"] == 217  # cada semana, sobre la anterior redondeada hacia abajo
    assert weeks[3]["aerobic"] == round(217 * 0.7)  # descarga
    assert all(b["aerobic"] <= a["aerobic"] * 1.1 + 1 for a, b in zip(weeks[:3], weeks[1:3]))


@pytest.mark.parametrize("adherence, direction", [(0.9, "up"), (0.7, "same"), (0.4, "down")])
def test_next_block_follows_adherence(adherence, direction):
    p = profile(level="beginner")
    first, second = hp.week_dose(p, [], 0)["aerobic"], hp.week_dose(p, [adherence], 0)["aerobic"]
    assert {"up": second > first, "same": second == first, "down": second < first}[direction]


def test_dose_never_passes_the_goal_target():
    p = profile(goal="fat_loss", level="advanced")
    assert all(hp.week_dose(p, [1.0] * k, w)["aerobic"] <= 250 for k in range(6) for w in range(4))


def test_older_adults_get_three_strength_days_with_balance():
    p = profile(age=70)
    assert hp.strength_days(p) == 3
    week = hp.week_plan(p, MONDAY)
    assert all(hp.BALANCE in s["exercises"] for s in sessions(week, "strength"))


# ---- §5 reparto semanal

def test_always_one_rest_day_and_strength_days_spread_out():
    week = hp.week_plan(profile(days=ALL_DAYS, level="intermediate"), MONDAY)
    assert any(not d["sessions"] for d in week["days"])
    s_days = [WEEKDAYS.index(d["weekday"]) for d in week["days"] if any(s["sport"] == "strength" for s in d["sessions"])]
    assert len(s_days) == 3 and min(b - a for a, b in zip(s_days, s_days[1:])) >= 2


def test_four_strength_days_split_upper_lower():
    p = profile(goal="strength", level="intermediate", days={d: 75 for d in ("MON", "TUE", "THU", "FRI", "SAT")})
    strength = sessions(hp.week_plan(p, MONDAY), "strength")
    assert [s["patterns"] for s in strength] == [list(hp.UPPER), list(hp.LOWER)] * 2


def test_strength_goal_never_runs_on_leg_day_if_there_is_an_alternative():
    p = profile(goal="strength", level="intermediate", cardio=["running", "cycling"], lthr=174, max_hr=192,
                days={d: 120 for d in ("MON", "TUE", "THU", "FRI", "SAT")})
    for d in hp.week_plan(p, MONDAY)["days"]:
        kinds = {s["sport"] for s in d["sessions"]}
        legs = any(s["sport"] == "strength" and "sentadilla" in s["patterns"] for s in d["sessions"])
        assert not (legs and "running" in kinds)


def test_warns_instead_of_stretching_sessions_when_days_are_short():
    week = hp.week_plan(profile(goal="fat_loss", level="advanced", days={"MON": 30, "THU": 30}), MONDAY)
    assert week["warnings"] and all(s["duration_min"] <= 30 for s in sessions(week))


@pytest.mark.parametrize("goal, minutes", [("fitness", 15), ("strength", 15), ("strength", 25)])
def test_strength_shrinks_to_fit_without_dropping_main_patterns(goal, minutes):
    days = {d: minutes for d in ("MON", "TUE", "THU", "FRI")}
    week = hp.week_plan(profile(goal=goal, level="advanced", days=days), MONDAY)
    for s in sessions(week, "strength"):
        assert s["sets_per_exercise"] >= 1 and len(s["patterns"]) >= 2
    assert week["warnings"]


def test_no_strength_is_allowed_but_flagged():
    week = hp.week_plan(profile(strength="none"), MONDAY)
    assert not sessions(week, "strength") and any("OMS" in w for w in week["warnings"])


# ---- §6 fuerza y §9 seguridad

def test_without_clearance_for_vigorous_there_is_no_vigorous_work():
    p = profile(goal="fat_loss", level="advanced", intensity_cap="light_moderate", cardio=["running", "cycling"],
                days=ALL_DAYS, lthr=174, max_hr=192)
    week = hp.week_plan(p, MONDAY)
    cardio = [s for s in sessions(week) if s["sport"] != "strength"]
    assert cardio and all(b["zone"] in ("Z1", "Z2", "Z3") for s in cardio for b in s["blocks"])
    assert all(s["rir"] >= 3 for s in sessions(week, "strength"))


def test_core_is_prescribed_by_time():
    s = hp.strength_session(profile(), hp.FULL_BODY, 2)
    assert any("Plancha" in e and "″" in e and "RIR" not in e for e in s["exercises"])


# ---- §8 sin reloj y §1 determinismo

@pytest.mark.parametrize("answers, status", [((4, 4, 4), "normal"), ((2, 2, 5), "easy"), ((1, 1, 1), "rest")])
def test_readiness(answers, status):
    assert hp.readiness(*answers) == status


def test_easy_day_is_shorter_and_lighter():
    p = profile(level="intermediate")
    day = next(d for d in hp.week_plan(p, MONDAY)["days"] if len(d["sessions"]) == 2)
    easier, note = hp.adjust_day(p, day["sessions"], "easy", 44)
    assert note and sum(s["duration_min"] for s in easier) < sum(s["duration_min"] for s in day["sessions"])
    strength = next(s for s in easier if s["sport"] == "strength")
    assert strength["rir"] == next(s for s in day["sessions"] if s["sport"] == "strength")["rir"] + 1


def test_deterministic():
    p = profile(goal="fat_loss", level="intermediate", cardio=["walking", "cycling"])
    assert hp.week_plan(p, MONDAY, [0.9], 1) == hp.week_plan(p, MONDAY, [0.9], 1)


# ---- API

def test_endpoint():
    r = TestClient(app).post("/health-plan", json={
        "goal": "fitness", "days": {"MON": 45, "WED": 45, "SAT": 60}, "cardio": ["walking", "swimming_pool"],
        "start": MONDAY.isoformat(), "readiness": {"sleep": 3, "energy": 3, "soreness": 3},
        "screening": {"active": False, "known_disease": False, "symptoms": False}})
    assert r.status_code == 200
    body = r.json()
    assert len(body["days"]) == 7 and body["dose"]["strength_days"] == 2
    assert body["screening"]["intensity_cap"] == "light_moderate"


def test_endpoint_stops_when_active_with_symptoms():
    r = TestClient(app).post("/health-plan", json={
        "goal": "fitness", "days": {"MON": 45}, "screening": {"active": True, "known_disease": False, "symptoms": True}})
    body = r.json()
    assert r.status_code == 200 and body["screening"]["clearance"] == "stop"
    assert all(not d["sessions"] for d in body["days"]) and "médico" in body["warnings"][0]
    assert TestClient(app).post("/health-plan", json={"goal": "x", "days": {}}).status_code == 422


def test_endpoint_without_screening_uses_no_cap():
    r = TestClient(app).post("/health-plan", json={"goal": "fitness", "days": {"MON": 60, "WED": 60, "SAT": 60},
                                                   "level": "advanced", "start": MONDAY.isoformat()})
    assert r.status_code == 200 and r.json()["screening"] is None and r.json()["days"]


def test_six_day_weeks_keep_the_floor_and_vary_the_load():
    p = profile(goal="fat_loss", level="intermediate", strength="gym", cardio=["walking", "cycling"],
                days={d: 60 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")}, lthr=165, max_hr=185)
    week = hp.week_plan(p, MONDAY, [0.95], 1)
    strength = {d["weekday"]: s for d in week["days"] for s in d["sessions"] if s["sport"] == "strength"}
    cardio = {d["weekday"]: s for d in week["days"] for s in d["sessions"] if s["sport"] != "strength"}
    assert week["equivalent_min"] >= hp.FLOOR_AEROBIC and hp.monotony(strength, cardio) < hp.MAX_MONOTONY
    rested = [d for d in week["days"] if d.get("note") == hp.REST_NOTE]
    assert all(not d["sessions"] for d in rested)  # el descanso extra, si lo hay, es un día sin sesiones


def test_monotony():
    assert hp.monotony({}, {}) == 0.0
    flat = {d: {"tss": 50} for d in WEEKDAYS[:6]}
    assert hp.monotony(flat, {}) > 2  # seis días iguales y uno de descanso


# ---- mejora 3: mover sesiones y cambiar días de la semana

def test_changes_recompute_from_the_change_day_and_keep_past_days():
    p = profile(level="intermediate")  # lun, mié, vie, sáb
    base = hp.week_plan(p, MONDAY)
    wednesday = MONDAY + timedelta(2)
    moved = hp.week_with_changes(p, MONDAY, [], 0, {"WED": 0, "THU": 60}, since=wednesday)
    days = {d["weekday"]: d for d in moved["days"]}
    assert moved["changed"] and not days["WED"]["sessions"] and days["THU"]["sessions"]
    assert moved["days"][:2] == base["days"][:2]  # lunes y martes ya pasaron: no cambian
    assert hp.week_with_changes(p, MONDAY, [], 0, None) == base


def test_adherence_counts_the_moved_plan():
    p = profile(level="intermediate")
    start = date(2026, 1, 5)
    changes = {start.isoformat(): ({"MON": 0, "TUE": 60}, start)}
    planned = hp.week_with_changes(p, start, [], 0, *changes[start.isoformat()])
    done = {d["date"]: len(d["sessions"]) for d in planned["days"]}  # hizo todo lo que tocaba esa semana
    with_changes = hp.adherence_history(p, start, done, 1, changes)
    without = hp.adherence_history(p, start, done, 1)
    assert with_changes[0] > without[0]  # sin tener en cuenta el cambio, el martes contaría como sesión de más


def test_never_drops_a_day_when_the_floor_is_not_reached():
    p = profile(days={d: 45 for d in WEEKDAYS}, cardio=["walking", "running", "cycling"])  # el caso real que lo destapó
    week = hp.week_plan(p, MONDAY)
    if week["equivalent_min"] < hp.FLOOR_AEROBIC:
        assert not any(d.get("note") == hp.REST_NOTE for d in week["days"])
        assert sum(bool(d["sessions"]) for d in week["days"]) == 6  # todos menos el descanso obligatorio
