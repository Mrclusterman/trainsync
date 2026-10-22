"""Casos de referencia del prototipo en Python para comprobar el port a Kotlin (shared/src/jvmTest).

Desde prototype/backend: `PYTHONPATH=. .venv/bin/python ../../tools/export_golden.py`. Escribe
shared/src/jvmTest/resources/golden/engine.json: entradas y salidas exactas del motor del prototipo.
"""
import json
from dataclasses import asdict
from datetime import date, timedelta
from pathlib import Path

from app.services import training_engine as e
from app.services.session_catalog import CATALOG

OUT = Path(__file__).resolve().parents[1] / "shared/src/jvmTest/resources/golden/engine.json"

PROFILES = {
    "vacío": dict(active_sports=["running"]),
    "corredor": dict(active_sports=["running", "cycling"], lthr=172, max_hr=190, run_model=(0.8, 0.0175),
                     day_minutes={"TUE": 60, "WED": 45, "THU": 75, "FRI": 40, "SAT": 120, "SUN": 150}),
    "triatleta": dict(active_sports=["swimming_pool", "cycling", "running"], lthr=168, lthr_by_sport={"cycling": 160},
                      css=105, strength="bodyweight", rest_days=["SUN"]),
    "nadador sin CSS": dict(active_sports=["swimming_pool", "swimming_open"], strength="none", rest_days=["MON", "FRI"]),
    "caminante": dict(active_sports=["walking", "cycling"], lthr=150, day_minutes={"MON": 30, "WED": 45, "SAT": 60},
                      rest_days=["TUE", "THU", "FRI", "SUN"], needs_test={"cycling"}),
}
GOALS = {
    "ninguno": None,
    "maratón": e.Goal("Maratón", "running", date(2026, 12, 6), "A", 42.195),
    "10k B": e.Goal("10K", "running", date(2026, 11, 15), "B", 10),
    "trail ultra": e.Goal("Ultra", "running", date(2027, 3, 1), "A", 80, True),
    "triatlón": e.Goal("Olímpico", "triathlon", date(2027, 2, 14), "A"),
    "gran fondo C": e.Goal("Gran fondo", "cycling", date(2026, 11, 1), "C", 160),
}
STATES = {
    "sin estado": None,
    "fresco": e.DayState(ctl=55, atl=45, sleep_score=82, load_focus={"low": 0.9, "high": 0.08, "anaerobic": 0.02}),
    "cargado": e.DayState(ctl=60, atl=85, consecutive_days=4, effort_mismatch=True),
    "mal dormido": e.DayState(ctl=40, atl=42, hrv_history=[60.0] * 28 + [40.0] * 7, sleep_score=38, days_since_activity=9),
    "muy cargado": e.DayState(ctl=50, atl=90),
}


def profile_json(p: dict) -> dict:
    d = dict(p)
    if "run_model" in d:
        d["run_model"] = list(d["run_model"])
    if "needs_test" in d:
        d["needs_test"] = sorted(d["needs_test"])
    return d


def goal_json(g):
    return None if g is None else {**asdict(g), "date": g.date.isoformat()}


def state_json(s):
    return None if s is None else asdict(s)


cases = {"build_session": [], "plan": [], "phase_for": [], "fit_days": [], "hr_pace_model": [], "monotony": []}

for c in CATALOG:
    for pname in ("vacío", "corredor", "triatleta"):
        prof = PROFILES[pname]
        for volume in (0.5, 1.0, 1.45):
            for max_minutes in (None, 80):
                inp = dict(kind=c["category"], sport=c["sport"], profile=pname, volume=volume, phase=c["phases"][0],
                           week=3, event=(c.get("events") or [None])[0], max_minutes=max_minutes)
                kw = {k: v for k, v in inp.items() if k != "profile"}
                out = e.build_session(profile=e.Profile(**prof), **kw)
                cases["build_session"].append({"in": inp, "out": out})

starts = [date(2026, 8, 31), date(2026, 10, 19), date(2026, 11, 30), date(2027, 2, 2)]
for pname, prof in PROFILES.items():
    for gname, goal in GOALS.items():
        for sname, state in STATES.items():
            for start in starts:
                if (hash((pname, gname, sname, start)) % 3) and sname != "sin estado":
                    continue  # muestra: todas las combinaciones sin estado y un tercio con estado
                out = e.plan(start, 14, e.Profile(**prof), goal, state)
                cases["plan"].append({"in": {"start": start.isoformat(), "days": 14, "profile": pname, "goal": gname,
                                             "state": sname}, "out": out})

for gname, goal in GOALS.items():
    for d in range(0, 400, 3):
        day = date(2026, 6, 1) + timedelta(d)
        cases["phase_for"].append({"in": {"day": day.isoformat(), "goal": gname}, "out": list(e.phase_for(day, goal, None))})

for phase in e.TEMPLATES:
    for rest in (["MON"], ["SUN"], ["MON", "SAT", "SUN"], ["TUE", "WED", "THU", "FRI", "SAT"], []):
        placed, moved = e.fit_days(e.TEMPLATES[phase], rest)
        cases["fit_days"].append({"in": {"phase": phase, "rest_days": rest}, "out": [placed, moved]})

samples = [(120 + (i % 60), 2.4 + (i % 60) * 0.02, (i % 7) - 3.0) for i in range(4000)]
cases["hr_pace_model"].append({"in": samples, "out": e.hr_pace_model(samples)})
cases["monotony"] += [{"in": loads, "out": list(e.monotony(loads))}
                      for loads in ([50, 0, 80, 40, 0, 120, 60], [30] * 7, [0, 0, 0, 100, 0, 0, 0])]

data = {"profiles": {k: profile_json(v) for k, v in PROFILES.items()},
        "goals": {k: goal_json(v) for k, v in GOALS.items()},
        "states": {k: state_json(v) for k, v in STATES.items()},
        "cases": cases}
OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print({k: len(v) for k, v in cases.items()}, f"{OUT.stat().st_size // 1024} KB")
