import re

import pytest

from app.services import training_engine as e
from app.services import workout_text as w
from app.services.session_catalog import CATALOG

PROFILE = e.Profile(active_sports=["running", "cycling"], lthr=174, max_hr=192, rest_hr=46)
STEP = re.compile(r"^- \S.* ((\d+m(\d+)?|\d+s) (\d+-\d+% LTHR|Z1 HR|\d+:\d\d-\d+:\d\d/100m Pace)|RPE [\d-]+ (\d+m(\d+)?|\d+s))$")


def test_duration():
    assert [w.duration(m) for m in (10, 0.5, 1.5, 0.17, 2.25)] == ["10m", "30s", "1m30", "10s", "2m15"]


def test_targets():
    assert w.target("Z4", "running", 174, None, None) == "95-100% LTHR"
    assert w.target("Z1", "cycling", 174, None, None) == "Z1 HR"
    assert w.target("Z6", "running", 174, None, None) == "RPE 10"
    assert w.target("Z3", "running", None, None, None) == "RPE 5"  # sin LTHR, por sensación
    assert w.target("Z2", "running", 174, None, 144) == "78-83% LTHR"  # techo LTHR − 30 por debajo de Z2
    assert w.target("Z4", "swimming_pool", None, 100, None) == "1:43-1:37/100m Pace"
    assert w.target("Z4", "swimming_pool", None, None, None) == "RPE 6-7"


def test_intervals_become_repeats():
    session = e.build_session("vo2max", "running", PROFILE, phase="base", week=1)
    text = w.describe(session, 174, None)
    work = next(b for b in session["blocks"] if b["reps"] > 1)
    assert f"\n{work['reps']}x\n- principal {w.duration(work['minutes'])} 100-107% LTHR\n- recuperación" in text


@pytest.mark.parametrize("entry", CATALOG, ids=lambda c: c["id"])
def test_every_catalog_session_converts(entry):
    profile = e.Profile(active_sports=[entry["sport"]], lthr=174, css=105)
    event = (entry.get("events") or (None,))[0]
    # La rotación semanal elige entre las sesiones válidas: se busca la semana en que sale esta
    session = next(s for week in range(12) if (s := e.build_session(entry["category"], entry["sport"], profile,
                   phase=entry["phases"][0], week=week, event=event))["id"] == entry["id"])
    text = w.describe(session, 174, 105)
    steps = [line for line in text.splitlines() if line.startswith("-")]
    assert steps and all(STEP.match(line) for line in steps), text
    # Las notas no pueden parecer repeticiones ("6x") ni pasos para el analizador de intervals.icu
    notes = [line for line in text.splitlines() if line and not line.startswith("-") and not re.fullmatch(r"\d+x", line)]
    assert not any(re.search(r"\b\d+x\b", n) for n in notes)


def test_field_test_uses_rpe_not_the_lthr_it_measures():
    session = e.build_session("test", "running", PROFILE)
    text = w.describe(session, 174, None)
    steps = [line for line in text.splitlines() if line.startswith("-")]
    assert not any("LTHR" in line for line in steps) and "- test RPE 6-7 30m" in steps


def test_event():
    session = e.build_session("aerobic", "cycling", PROFILE)
    ev = w.event(session, "2026-10-20", 174, None)
    assert ev["external_id"] == "trainsync-2026-10-20" and ev["type"] == "Ride" and ev["category"] == "WORKOUT"
    assert ev["start_date_local"] == "2026-10-20T00:00:00" and ev["moving_time"] == session["duration_min"] * 60
