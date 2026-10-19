from datetime import date, timedelta

import pytest

from app.services import training_engine as e
from app.services.session_catalog import CATALOG, Z3_ALLOWED

PROFILE = e.Profile(active_sports=["running", "cycling"], lthr=174, max_hr=192, rest_hr=46)
GOAL = e.Goal(name="Maratón", sport="running", date=date(2027, 3, 7))


def test_formulas_match_spec_examples():
    assert e.strength_tss(7, 60) == pytest.approx(49)  # ejemplo de la sección 8.1
    assert e.hr_tss(3600, 174, 46, 174) == pytest.approx(100)  # 1 h a LTHR = 100
    assert e.power_tss(3600, 250, 250) == pytest.approx(100)
    assert e.swim_tss(3600, 90, 90) == pytest.approx(100)
    assert e.next_load(40, 40, e.K_CTL) == 40


BASELINE = [85, 115] * 14  # 28 días; CV ~15 %, como el RMSSD diario real (Heikura 2015)


@pytest.mark.parametrize("last7,expected", [
    ([100] * 7, "within_swc"),
    ([80] * 7, "outside_swc"),   # caída sostenida
    ([125] * 7, "outside_swc"),  # subida sostenida: también baja intensidad
    ([100, None, None, None, None, None, 100], None),  # < 3 medidas en 7 días
])
def test_hrv_status_rolling_vs_swc(last7, expected):
    assert e.hrv_status(BASELINE + last7) == expected


def test_single_bad_day_does_not_trigger_hrv():
    assert e.hrv_status(BASELINE + [100] * 6 + [80]) == "within_swc"


def test_hrv_needs_baseline():
    assert e.hrv_status([100] * 10) is None


def test_zone_targets_from_lthr_and_css():
    # Friel: running Z2 85-89 %, ciclismo Z2 81-89 %; Z3 empieza en 90 % = 156,6
    assert e.zone_target("Z2", "running", 174, None) == "148-156 ppm"
    assert e.zone_target("Z1", "running", 174, None) == "< 148 ppm"
    assert e.zone_target("Z2", "cycling", 174, None) == "141-156 ppm"
    assert e.zone_target("Z4", "swimming_pool", None, 100) == "1:37-1:43/100m"
    assert e.zone_target("Z2", "running", None, None) is None


def test_gray_zone_only_on_easy_sessions():
    assert e.gray_zone([1000, 1500, 600, 0, 0, 0, 0])  # 19 % Z3, sin Z4+
    assert not e.gray_zone([1000, 1500, 600, 600, 0, 0, 0])  # sesión de calidad
    assert not e.gray_zone([])


def test_phases_progress_to_taper():
    start = GOAL.date - timedelta(weeks=20)
    phases = [e.phase_for(start + timedelta(weeks=w), GOAL, start)[0] for w in range(20)]
    assert phases[0] == "base_early" and phases[5] == "base"
    assert phases.index("build") < phases.index("peak") < phases.index("taper")
    assert phases[-1] == "taper"
    assert e.phase_for(date(2026, 10, 19), None, None) == ("base", False)


def test_bad_hrv_and_sleep_turn_quality_into_recovery():
    # Criterio de éxito de la Fase 3: HRV bajo + mal sueño = recuperación
    kind, volume, reasons = e.adjust("vo2max", e.DayState(ctl=40, atl=45, hrv_history=BASELINE + [80] * 7, sleep_score=40))
    assert kind == "recovery" and volume == 0.7 and len(reasons) == 2


def test_very_negative_tsb_forces_recovery():
    assert e.adjust("long", e.DayState(ctl=40, atl=75))[0] == "recovery"


def test_loaded_athlete_keeps_quality():
    # TSB -20 es zona productiva de carga (Friel): no se baja la sesión
    assert e.adjust("vo2max", e.DayState(ctl=40, atl=60, hrv_history=BASELINE + [100] * 7, sleep_score=80)) == ("vo2max", 1.0, [])


def test_easy_sessions_have_aerobic_threshold_cap():
    assert e.build_session("aerobic", "running", PROFILE)["hr_cap"] == 144
    assert e.build_session("vo2max", "running", PROFILE)["hr_cap"] is None
    assert e.build_session("aerobic", "running", PROFILE)["targets"] == {"Z1": "< 144 ppm", "Z2": "<= 144 ppm"}
    assert e.build_session("aerobic", "cycling", PROFILE)["targets"]["Z2"] == "141-144 ppm"


def test_two_week_plan_is_polarized_and_complete():
    days = e.plan(date(2026, 10, 19), 14, PROFILE, GOAL)
    assert len(days) == 14
    sessions = [d["main"] for d in days if d["main"]]
    # Regla 1 de zona gris: Z3 nunca es objetivo de ningún bloque en base
    assert all(b["zone"] != "Z3" for s in sessions for b in s["blocks"])
    assert any(d["strength"] for d in days)
    for d in days:
        if d["main"]:
            assert [a["sport"] for a in d["alternatives"]] == ["cycling"]
            assert d["alternatives"][0]["tss"] == pytest.approx(d["main"]["tss"], rel=0.15)


def test_taper_has_no_strength():
    days = e.plan(GOAL.date - timedelta(days=6), 7, PROFILE, GOAL)
    assert {d["phase"] for d in days} == {"taper"}
    assert not any(d["strength"] for d in days)


def test_triathlon_rotates_sports():
    profile = e.Profile(active_sports=["running", "cycling", "swimming_pool"], strength="bodyweight")
    goal = e.Goal(name="70.3", sport="triathlon", date=date(2027, 6, 1))
    sports = {d["main"]["sport"] for d in e.plan(date(2026, 10, 19), 7, profile, goal) if d["main"]}
    assert sports == {"running", "cycling", "swimming_pool"}


@pytest.mark.parametrize("sport", ["running", "cycling", "swimming_pool"])
def test_catalog_covers_every_template_slot(sport):
    for phase, template in e.TEMPLATES.items():
        for kind in set(template.values()) | {"recovery", "aerobic"}:  # recovery/aerobic: destinos de DOWNGRADE
            assert e.pick_session(kind, sport, phase, 0), (sport, phase, kind)


def test_z3_only_in_allowed_sessions():
    for c in CATALOG:
        if c["id"] not in Z3_ALLOWED:
            assert all(b["zone"] != "Z3" for b in c["blocks"]), c["id"]


def test_sessions_rotate_weekly():
    ids = {e.pick_session("vo2max", "running", "base_early", w)["id"] for w in range(4)}
    assert len(ids) > 1


def test_open_water_only_in_build_and_peak():
    profile = e.Profile(active_sports=["swimming_pool", "swimming_open"])
    assert e.build_session("race_specific", "swimming_open", profile, phase="base") is None
    assert e.build_session("race_specific", "swimming_open", profile, phase="peak")["id"] == "S-OW"


def test_swim_without_css_uses_rpe():
    s = e.build_session("threshold", "swimming_pool", e.Profile(active_sports=["swimming_pool"]), phase="build")
    assert s["targets"]["Z4"] == "RPE 6-7"


def test_running_long_capped_at_150_minutes():
    assert e.build_session("long", "running", PROFILE, volume=3, max_minutes=e.long_cap(None, "running"))["duration_min"] <= 150


def test_strength_dose_by_phase():
    base, build = e.build_strength("running", PROFILE, "base"), e.build_strength("running", PROFILE, "build")
    assert "4–8 RM" in base["dose"] and base["order"].startswith("antes")
    assert "Saltos al cajón" in build["exercises"] and "Saltos al cajón" not in base["exercises"]
    assert build["order"].startswith("después")


def test_weekly_load_follows_ctl_ramp():
    state = e.DayState(ctl=40, atl=40)
    days = e.plan(date(2026, 10, 19), 7, PROFILE, GOAL, state)
    target = e.weekly_tss_target(40)  # 7 × (40 + 3 / 0,1535) ≈ 417
    assert target == pytest.approx(416.8, abs=1)
    assert sum(e.day_tss(d) for d in days) == pytest.approx(target, rel=0.15)


# ---------------------------------------------------------------- RPE

def test_every_block_has_rpe_and_short_intervals_guide_by_rpe():
    week = next(w for w in range(5) if e.pick_session("vo2max", "running", "base_early", w)["id"] == "R-VO2-2")
    s = e.build_session("vo2max", "running", PROFILE, phase="base_early", week=week)  # 8×2′
    assert all("rpe" in b for b in s["blocks"])
    assert any("guide" in b for b in s["blocks"])


def test_high_rpe_with_low_hr_removes_intensity():
    assert e.effort_mismatch(7, [1200, 2400, 100, 0, 0, 0, 0])
    assert not e.effort_mismatch(7, [600, 1200, 900, 600, 0, 0, 0])  # sesión dura: RPE alto esperado
    assert not e.effort_mismatch(None, [1200, 2400, 0])
    kind, _, reasons = e.adjust("vo2max", e.DayState(ctl=40, atl=40, effort_mismatch=True))
    assert kind == "aerobic" and "percibido" in reasons[0]


def test_monotony_and_strain():
    load, mono, strain = e.monotony([300, 300, 300, 300, 300, 300, 310])  # carga casi idéntica: monotonía alta
    assert load == 2110 and mono > 2 and strain > load
    assert e.monotony([0] * 7)[1] is None


# ---------------------------------------------------------------- tipos de prueba

def goal(sport="running", km=None, trail=False, weeks=20):
    return e.Goal("X", sport, date(2026, 10, 19) + timedelta(weeks=weeks), distance_km=km, trail=trail)


def test_taper_length_depends_on_distance():
    marathon, tenk = goal(km=42.2), goal(km=10)
    assert e.phase_for(marathon.date - timedelta(days=20), marathon, None)[0] == "taper"
    assert e.phase_for(tenk.date - timedelta(days=20), tenk, None)[0] == "peak"
    assert e.phase_for(tenk.date - timedelta(days=8), tenk, None)[0] == "taper"


def test_long_run_cap_by_distance():
    assert e.long_cap(goal(km=5), "running") == 75 and e.long_cap(goal(km=100), "running") == 240
    assert e.long_cap(goal(km=42.2), "cycling") is None


def test_templates_by_event():
    assert e.template_for("build", goal(km=21.1))["THU"] == "threshold"
    assert e.template_for("build", goal(km=10))["THU"] == "vo2max"
    assert e.template_for("peak", goal(km=80))["SAT"] == "long"
    assert e.template_for("build", goal("triathlon"))["SAT"] == "brick"


def test_trail_sessions_only_for_trail_goals():
    trail = {e.pick_session("long", "running", "base", w, "trail")["id"] for w in range(2)}
    road = {e.pick_session("long", "running", "base", w)["id"] for w in range(2)}
    assert "T-LONG" in trail and "R-LONG" in trail
    assert road == {"R-LONG"}


def test_swimming_goal_plans_swimming():
    profile = e.Profile(active_sports=["swimming_pool", "running"], css=95)
    days = e.plan(date(2026, 10, 19), 7, profile, goal("swimming_pool", km=1.5))
    assert {d["main"]["sport"] for d in days if d["main"]} == {"swimming_pool"}


def test_open_water_long_in_build():
    profile = e.Profile(active_sports=["swimming_open"])
    assert e.build_session("long", "swimming_open", profile, phase="build")["id"] == "S-OW-LONG"


# ---------------------------------------------------------------- pendientes completados

def test_base_has_neuromuscular_day():
    d = e.propose_day(date(2026, 10, 29) + timedelta(weeks=5), PROFILE, GOAL)  # jueves en base
    assert d["phase"] == "base" and d["main"]["type"] == "neuromuscular"


def test_brick_on_saturday_in_build_for_triathlon():
    profile = e.Profile(active_sports=["running", "cycling", "swimming_pool"], css=95)
    g = goal("triathlon", weeks=10)
    sat = next(d for d in e.plan(date(2026, 10, 19), 14, profile, g) if d["weekday"] == "SAT" and d["phase"] == "build")
    assert sat["main"]["id"] == "C-BRICK"


def test_missing_physiology_schedules_test_once():
    profile = e.Profile(active_sports=["running", "cycling"], lthr=174, needs_test={"running"})
    days = e.plan(date(2026, 10, 19), 14, profile, GOAL)
    tests = [d for d in days if d["main"] and d["main"]["type"] == "test"]
    assert len(tests) == 1 and tests[0]["main"]["id"] == "R-TEST-LTHR"
    assert tests[0]["main"]["targets"] == {}  # sin objetivos de FC: se corre al máximo sostenible


def test_intermittent_duty_lowers_tss():
    block = [{"reps": 1, "minutes": 13, "zone": "Z5"}]
    assert e._blocks_tss([block[0] | {"duty": 0.67}]) < e._blocks_tss(block)


def test_long_session_gets_largest_share_of_weekly_tss():
    days = e.plan(date(2026, 10, 19), 7, PROFILE, GOAL, e.DayState(ctl=40, atl=40))
    long_day = next(d for d in days if d["main"] and d["main"]["type"] == "long")
    assert long_day["main"]["tss"] == max(d["main"]["tss"] for d in days if d["main"])


def test_phase_progresses_without_plan_start():
    g = goal(km=42.2, weeks=30)
    phases = [e.phase_for(date(2026, 10, 19) + timedelta(weeks=w), g, None)[0] for w in range(30)]
    assert phases[0] == "base" and "base_early" in phases and "build" in phases and phases[-1] == "taper"
