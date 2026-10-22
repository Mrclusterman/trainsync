"""Casos de referencia de los planes de salud del prototipo (shared/src/jvmTest/resources/golden/health.json).

Desde prototype/backend: `PYTHONPATH=. .venv/bin/python ../../tools/export_golden_health.py`.
"""
import json
from datetime import date, timedelta
from pathlib import Path

from app.services import health_plan as hp

OUT = Path(__file__).resolve().parents[1] / "shared/src/jvmTest/resources/golden/health.json"
W = hp.WEEKDAYS
PROFILES = {
    "principiante": dict(goal="fitness", days={"MON": 45, "WED": 45, "SAT": 60}, strength="bodyweight",
                         cardio=["walking"], level="beginner", intensity_cap="light_moderate"),
    "mayor": dict(goal="fitness", days={"MON": 60, "TUE": 45, "THU": 60, "SAT": 60}, strength="bodyweight",
                  cardio=["walking", "swimming_pool"], level="beginner", age=70, intensity_cap="light_moderate", css=150),
    "gimnasio": dict(goal="strength", days={d: 75 for d in ("MON", "TUE", "THU", "FRI", "SAT")}, strength="gym",
                     cardio=["cycling", "running"], level="intermediate", lthr=170, max_hr=190),
    "corredor": dict(goal="fitness", days={"MON": 60, "TUE": 75, "WED": 60, "THU": 75, "SAT": 120, "SUN": 90},
                     strength="gym", cardio=["running", "cycling"], level="advanced", lthr=172, max_hr=190),
    "seis días": dict(goal="fitness", days={d: 60 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")}, strength="gym",
                      cardio=["walking", "cycling"], level="intermediate", lthr=165, max_hr=185),
    "todos los días": dict(goal="fitness", days={d: 45 for d in W}, cardio=["walking", "running", "cycling"],
                           level="intermediate", lthr=170),
    "sin fuerza": dict(goal="fitness", days={"TUE": 30, "THU": 30}, strength="none", cardio=["cycling"], level="advanced"),
    "cuatro de fuerza": dict(goal="strength", days={d: 40 for d in ("MON", "TUE", "WED", "THU", "FRI")}, strength="gym",
                             cardio=["swimming_pool", "walking"], level="advanced", intensity_cap="moderate", css=110,
                             keep=["FRI"]),
}


def mk(name):
    d = dict(PROFILES[name])
    d["keep"] = frozenset(d.get("keep", []))
    return hp.HealthProfile(**d)


def planned(s):
    return s  # dicts tal cual (fuerza o resistencia)


cases = {"week_plan": [], "week_with_changes": [], "adjust_day": [], "match_day": [], "adherence": [],
         "alternatives": [], "spread": [], "week_dose": []}
mondays = [date(2026, 1, 5), date(2026, 10, 19), date(2027, 3, 1)]
for name in PROFILES:
    p = mk(name)
    for monday in mondays:
        for adherence in ([], [0.95], [0.95, 0.5], [0.7, 0.9, 0.3]):
            for wib in range(4):
                cases["week_plan"].append({"in": {"profile": name, "start": monday.isoformat(), "adherence": adherence,
                                                  "week_in_block": wib},
                                           "out": hp.week_plan(p, monday, adherence, wib)})
    week = hp.week_plan(p, mondays[1], [0.9], 1)
    for changes, since in (({"MON": 0, "SUN": 60}, mondays[1]), ({"WED": 0}, mondays[1] + timedelta(2)),
                           ({d: 30 for d in W}, mondays[1] + timedelta(4))):
        cases["week_with_changes"].append({"in": {"profile": name, "start": mondays[1].isoformat(), "adherence": [0.9],
                                                  "week_in_block": 1, "changes": changes, "since": since.isoformat()},
                                           "out": hp.week_with_changes(p, mondays[1], [0.9], 1, changes, since)})
    for day in week["days"]:
        for status in ("easy", "rest"):
            sessions, note = hp.adjust_day(p, day["sessions"], status, 43)
            cases["adjust_day"].append({"in": {"profile": name, "sessions": day["sessions"], "status": status, "week": 43},
                                        "out": {"sessions": sessions, "note": note}})
        for s in day["sessions"]:
            if s["sport"] != "strength":
                cases["alternatives"].append({"in": {"profile": name, "session": s, "week": 43},
                                              "out": hp.alternatives(p, s, 43)})
        for done in ([{"sport": "cycling", "source": "watch", "minutes": 50, "start": "08:00"}],
                     [{"sport": "running", "source": "watch", "minutes": 12, "start": None},
                      {"sport": "walking", "source": "manual", "minutes": None, "start": None}],
                     [{"sport": "strength", "source": "manual", "minutes": None, "start": None},
                      {"sport": "strength", "source": "watch", "minutes": 40, "start": "19:30"}]):
            cases["match_day"].append({"in": {"sessions": day["sessions"], "done": done},
                                       "out": hp.match_day(day["sessions"], done)})
    start = date(2026, 1, 5)
    done = {}
    for wk in range(8):
        for d in hp.week_plan(p, start + timedelta(weeks=wk), [], wk % 4)["days"]:
            if (wk + W.index(d["weekday"])) % 3:
                done[d["date"]] = [{"sport": s["sport"], "source": "manual", "minutes": None, "start": None}
                                   for s in d["sessions"]]
    changes = {start.isoformat(): ({"MON": 0, "SUN": 40}, start)}
    cases["adherence"].append({"in": {"profile": name, "block_start": start.isoformat(), "done": done, "blocks": 2,
                                      "changes": {k: [v[0], v[1].isoformat()] for k, v in changes.items()}},
                               "out": hp.adherence_history(p, start, done, 2, changes)})
    cases["week_dose"] += [{"in": {"profile": name, "adherence": a, "week_in_block": w}, "out": hp.week_dose(p, a, w)}
                           for a in ([], [1.0], [0.5, 0.9], [0.9, 0.9, 0.9, 0.9]) for w in range(4)]

for days in (W, ["MON", "WED", "FRI"], ["TUE", "THU", "SAT", "SUN"], ["MON", "TUE"]):
    for n in range(len(days) + 1):
        cases["spread"].append({"in": {"days": days, "n": n}, "out": hp.spread(days, n)})

data = {"profiles": {k: {**v, "keep": sorted(v.get("keep", []))} for k, v in PROFILES.items()}, "cases": cases}
OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print({k: len(v) for k, v in cases.items()}, f"{OUT.stat().st_size // 1024} KB")
