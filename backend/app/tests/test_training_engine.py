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
    assert s["targets"]["Z4"] == "RPE 7"


def test_running_long_capped_at_150_minutes():
    assert e.build_session("long", "running", PROFILE, volume=3)["duration_min"] <= 150


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
