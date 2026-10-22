"""Casos de referencia del texto para el reloj y de los datos de intervals.icu (golden/intervals.json).

Desde prototype/backend: `PYTHONPATH=. .venv/bin/python ../../tools/export_golden_intervals.py`. Datos sintéticos:
ningún dato de una cuenta real.
"""
import json
import random
from dataclasses import asdict
from datetime import date, timedelta
from pathlib import Path

from app.routers import me, plan
from app.services import health_plan as hp, training_engine as e, workout_text as wt
from app.services.session_catalog import CATALOG

OUT = Path(__file__).resolve().parents[1] / "shared/src/jvmTest/resources/golden/intervals.json"
rnd = random.Random(7)
PROFILES = {
    "fc": dict(active_sports=["running", "cycling"], lthr=172, max_hr=190),
    "ritmo y potencia": dict(active_sports=["running", "cycling"], lthr=170, max_hr=188, run_pace=265, ftp=250),
    "modelo": dict(active_sports=["running"], lthr=174, max_hr=192, run_model=(0.9, 0.0165)),
    "natación": dict(active_sports=["swimming_pool", "swimming_open"], css=98),
    "sin datos": dict(active_sports=["running", "walking"]),
}


def prof(name):
    d = dict(PROFILES[name])
    return e.Profile(**d)


cases = {"describe": [], "strength_event": [], "fill_physiology": [], "day_state": [], "race_to_goal": [],
         "pick_goal": [], "last_activity": [], "watch_sessions": []}
for c in CATALOG:
    for name in PROFILES:
        p = prof(name)
        s = e.build_session(c["category"], c["sport"], p, 1.0, c["phases"][0], 2, (c.get("events") or [None])[0])
        cases["describe"].append({"in": {"session": s, "profile": name, "day": "2026-10-22", "key": c["sport"]},
                                  "out": wt.event(s, "2026-10-22", p, key=c["sport"])})
for level in hp.LEVELS:
    for kit in ("gym", "bodyweight"):
        hpp = hp.HealthProfile("fitness", {"MON": 60}, strength=kit, level=level, age=70 if kit == "bodyweight" else 30)
        s = hp.strength_session(hpp, hp.FULL_BODY, 2)
        cases["strength_event"].append({"in": {"session": s, "day": "2026-10-23"}, "out": wt.strength_event(s, "2026-10-23")})

ATHLETES = [
    {"icu_resting_hr": 48, "sportSettings": [{"types": ["Run", "VirtualRun"], "lthr": 172, "max_hr": 190, "threshold_pace": 3.8},
                                             {"types": ["Ride"], "lthr": 165, "max_hr": 188, "ftp": 240},
                                             {"types": ["Swim"], "threshold_pace": 1.05}]},
    {"sportSettings": [{"types": ["Run"], "lthr": 190, "max_hr": 192}, {"types": ["Ride"], "lthr": 160, "max_hr": 185}]},
    {"sportSettings": [{"types": ["Ride", "GravelRide"], "lthr": 158}]},
    {},
]
for i, athlete in enumerate(ATHLETES):
    for sports in (["running", "cycling"], ["swimming_pool", "running"]):
        for lthr in (None, 168):
            p = e.Profile(active_sports=sports, lthr=lthr)
            out = plan.fill_physiology(p, athlete)
            cases["fill_physiology"].append({"in": {"athlete": athlete, "active_sports": sports, "lthr": lthr},
                                             "out": {**asdict(out), "needs_test": sorted(out.needs_test)}})

TYPES = ["Run", "Ride", "Swim", "Walk", "TrailRun", "WeightTraining", "Hike", "Yoga", "VirtualRide"]
for k in range(6):
    today = date(2026, 10, 22) + timedelta(k * 9)
    wellness = [{"id": (today - timedelta(d)).isoformat(), "ctl": 40 + rnd.random() * 30, "atl": 35 + rnd.random() * 50,
                 "hrv": rnd.choice([None, 45 + rnd.random() * 30]), "sleepScore": rnd.choice([None, 30, 55, 88])}
                for d in range(40)]
    if k == 3:
        for w in wellness[:5]:
            w["ctl"] = None
    acts = []
    for d in range(40):
        if rnd.random() < 0.65:
            start = f"{(today - timedelta(d)).isoformat()}T0{rnd.randint(6, 9)}:{rnd.randint(10, 59)}:00"
            zones = [rnd.randint(0, 1500) for _ in range(7)] if rnd.random() < 0.8 else None
            acts.append({"id": f"i{k}{d}", "name": f"Actividad {d}", "type": rnd.choice(TYPES), "start_date_local": start,
                         "moving_time": rnd.choice([300, 900, 2400, 4000]), "icu_training_load": rnd.choice([0, 30, 70, None]),
                         "icu_hr_zone_times": zones, "icu_rpe": rnd.choice([None, 3, 6, 8]),
                         "average_heartrate": rnd.choice([None, 140])})
    s = plan.day_state(wellness, acts, today)
    cases["day_state"].append({"in": {"wellness": wellness, "activities": acts, "today": today.isoformat()}, "out": asdict(s)})
    cases["last_activity"].append({"in": acts, "out": plan.last_activity(acts)})

    class Fake:
        def activities(self, *_):
            return acts
    cases["watch_sessions"].append({"in": acts, "out": me.watch_sessions(Fake(), today)})

RACES = [{"name": "Maratón de Valencia", "type": "Run", "category": "RACE_A", "distance": 42195,
          "start_date_local": "2026-12-06T00:00:00"},
         {"name": "Triatlón olímpico", "type": "Swim", "category": "RACE_B", "start_date_local": "2027-06-01T00:00:00"},
         {"name": "Ultra Trail", "type": "TrailRun", "category": "RACE_C", "distance": 80000, "start_date_local": "2027-03-01T00:00:00"},
         {"name": "", "type": "Ride", "start_date_local": "2026-11-01T00:00:00"},
         {"name": "Duatlón", "type": "Run", "category": "RACE_A", "start_date_local": "2026-09-01T00:00:00"}]
for r in RACES:
    g = plan.race_to_goal(r)
    cases["race_to_goal"].append({"in": r, "out": {**asdict(g), "date": g.date.isoformat()}})
goals = [plan.race_to_goal(r) for r in RACES]
for today in (date(2026, 8, 1), date(2026, 11, 2), date(2027, 1, 1), date(2027, 7, 1)):
    g = plan.pick_goal(goals, today)
    cases["pick_goal"].append({"in": {"races": RACES, "today": today.isoformat()}, "out": g and g.name})

OUT.write_text(json.dumps(cases, ensure_ascii=False, separators=(",", ":"), default=str))
print({k: len(v) for k, v in cases.items()}, f"{OUT.stat().st_size // 1024} KB")
