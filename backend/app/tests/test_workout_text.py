import re

import pytest

from app.services import training_engine as e
from app.services import workout_text as w
from app.services.session_catalog import CATALOG

# LTHR 174, FC máx. 192, ritmo umbral 4:41/km, CSS 2:00/100m, sin FTP: los umbrales de la cuenta de pruebas
PROFILE = e.Profile(active_sports=["running", "cycling"], lthr=174, max_hr=192, rest_hr=46, run_pace=281, css=120)
TARGET = r"(\d+(\.\d)?-\d+(\.\d)?% HR|\d+-\d+%|\d+:\d\d-\d+:\d\d/(km|100m) Pace)"
STEP = re.compile(rf"^- \S.* ((\d+m(\d+)?|\d+s) {TARGET}|RPE [\d-]+ (\d+m(\d+)?|\d+s))$|^- descanso (\d+m(\d+)?|\d+s)$")


def steps(text: str) -> list[str]:
    return [line for line in text.splitlines() if line.startswith("-")]


def test_duration():
    assert [w.duration(m) for m in (10, 0.5, 1.5, 0.17, 2.25, 30.0167)] == ["10m", "30s", "1m30", "10s", "2m15", "30m"]


def test_hr_targets_are_trainsync_bpm_as_percent_of_max_hr():
    run = {"sport": "running", "type": "aerobic", "id": "R-Z2", "hr_cap": 144}
    # Z4 de running = 95-100 % de LTHR 174 = 165,3-174 ppm = 86,1-90,6 % de 192
    assert w.target("Z4", run, PROFILE, "hr") == "86.1-90.6% HR"
    assert w.target("Z2", run, PROFILE, "hr") == "70.5-75% HR"  # techo LTHR − 30 = 144 ppm
    assert w.target("Z1", run, PROFILE, "hr") == "50-75% HR"
    assert w.target("Z6", run, PROFILE, "hr") is None


def test_pace_and_power_targets():
    run = {"sport": "running", "type": "threshold", "id": "R-THR-CRUISE"}
    assert w.target("Z4", run, PROFILE, "pace") == "4:55-4:38/km Pace"  # 105-99 % de 4:41
    swim = {"sport": "swimming_pool", "type": "threshold", "id": "S-THR"}
    assert w.target("Z4", swim, PROFILE, "pace") == "2:04-1:56/100m Pace"
    assert w.target("Z1", swim, PROFILE, "pace") == "2:42-2:24/100m Pace"
    ride = {"sport": "cycling", "type": "threshold", "id": "C-THR"}
    assert w.target("Z2", ride, PROFILE, "power") == "56-75%"


def test_one_metric_per_session():
    p = PROFILE
    assert w.metric({"sport": "running", "type": "threshold", "id": "R-THR-CRUISE"}, p) == "pace"
    assert w.metric({"sport": "running", "type": "aerobic", "id": "R-Z2"}, p) == "hr"
    assert w.metric({"sport": "running", "type": "threshold", "id": "T-HILL-THR"}, p) == "hr"  # trail
    assert w.metric({"sport": "cycling", "type": "threshold", "id": "C-THR"}, p) == "hr"  # sin FTP
    assert w.metric({"sport": "cycling", "type": "threshold", "id": "C-THR"}, e.Profile(["cycling"], ftp=250)) == "power"
    assert w.metric({"sport": "swimming_pool", "type": "aerobic", "id": "S-Z2"}, e.Profile(["swimming_pool"])) == "rpe"
    assert w.metric({"sport": "running", "type": "test", "id": "R-TEST-LTHR"}, p) == "rpe"


def test_intervals_become_repeats_with_pace():
    session = e.build_session("vo2max", "running", PROFILE, phase="base", week=1)
    text = w.describe(session, PROFILE)
    work = next(b for b in session["blocks"] if b["reps"] > 1)
    assert f"\n{work['reps']}x\n- principal {w.duration(work['minutes'])} 4:41-4:13/km Pace\n- recuperación" in text
    assert not any("HR" in line for line in steps(text))  # una sola métrica


def test_swim_rest_has_no_target():
    session = e.build_session("threshold", "swimming_pool", PROFILE, phase="build")
    rests = [s for s in steps(w.describe(session, PROFILE)) if "descanso" in s]
    assert rests and all("Pace" not in s for s in rests)


def test_field_test_uses_rpe_not_the_threshold_it_measures():
    text = w.describe(e.build_session("test", "running", PROFILE), PROFILE)
    assert "- test RPE 6-7 30m" in steps(text) and not any("Pace" in s or "HR" in s for s in steps(text))


@pytest.mark.parametrize("entry", CATALOG, ids=lambda c: c["id"])
def test_every_catalog_session_converts(entry):
    profile = e.Profile(active_sports=[entry["sport"]], lthr=174, max_hr=192, run_pace=281, css=105, ftp=250)
    event = (entry.get("events") or (None,))[0]
    # La rotación semanal elige entre las sesiones válidas: se busca la semana en que sale esta
    session = next(s for week in range(12) if (s := e.build_session(entry["category"], entry["sport"], profile,
                   phase=entry["phases"][0], week=week, event=event))["id"] == entry["id"])
    text = w.describe(session, profile)
    assert steps(text) and all(STEP.match(line) for line in steps(text)), text
    kinds = {k for k in ("HR", "Pace", "%") if any(k in s and (k != "%" or "HR" not in s) for s in steps(text))}
    assert len(kinds - {"%"}) <= 1  # nunca ritmo y FC en la misma sesión
    notes = [line for line in text.splitlines() if line and not line.startswith("-") and not re.fullmatch(r"\d+x", line)]
    assert not any(re.search(r"\b\d+x\b", n) for n in notes)


def test_event():
    session = e.build_session("aerobic", "cycling", PROFILE)
    ev = w.event(session, "2026-10-20", PROFILE)
    assert ev["external_id"] == "trainsync-2026-10-20" and ev["type"] == "Ride" and ev["category"] == "WORKOUT"
    assert ev["start_date_local"] == "2026-10-20T00:00:00" and ev["moving_time"] == session["duration_min"] * 60


def test_with_a_model_every_running_session_goes_by_pace():
    profile = e.Profile(active_sports=["running"], lthr=174, max_hr=192, run_model=(-0.9, 0.024))
    aerobic = e.build_session("aerobic", "running", profile)
    assert w.metric(aerobic, profile) == "pace"
    text = w.describe(aerobic, profile)
    # techo de sesión fácil 144 ppm → 6:31/km con esta recta; el reloj vigila el ritmo
    assert all("/km Pace" in s for s in steps(text)) and "-6:31/km Pace" in text
