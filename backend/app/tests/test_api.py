"""API completa contra un intervals.icu simulado (httpx.MockTransport)."""
import json
from datetime import date, timedelta

import httpx
from fastapi.testclient import TestClient

from app import links
from app.main import app
from app.routers import plan as plan_router
from app.services.intervals_client import IntervalsClient

TODAY = date.today()
api = TestClient(app)


def linked_user(monkeypatch, handler) -> dict:
    """Usuario nuevo con su cuenta de intervals.icu (simulada) vinculada; devuelve la cabecera de sesión."""
    import base64
    import os
    import uuid

    monkeypatch.setenv("ENCRYPTION_KEY", base64.b64encode(os.urandom(32)).decode())
    monkeypatch.setattr(links, "make_client", lambda a, k: IntervalsClient(a, k, httpx.MockTransport(handler)))
    token = api.post("/auth/register", json={"email": f"{uuid.uuid4().hex}@x.es", "password": "una-clave-larga",
                                             "birth_year": 1990, "accept_terms": True, "health_consent": True}).json()["token"]
    h = {"Authorization": f"Bearer {token}"}
    assert api.put("/me/intervals", json={"athlete_id": "i1", "api_key": "clave-de-pruebas-1234"}, headers=h).status_code == 204
    return h


def fake_intervals(request: httpx.Request) -> httpx.Response:
    assert request.headers["authorization"].startswith("Basic ")
    path = request.url.path
    if path.endswith("/wellness"):
        # Línea base ~70 ms; última semana ~55 ms: HRV fuera de rango
        return httpx.Response(200, json=[{"id": (TODAY - timedelta(d)).isoformat(), "ctl": 40, "atl": 45,
                                          "hrv": 55 if d < 7 else 66 + d % 2 * 8, "sleepScore": 45} for d in range(36)])
    if path.endswith("/activities"):
        return httpx.Response(200, json=[{"start_date_local": f"{(TODAY - timedelta(1)).isoformat()}T08:00:00",
                                          "type": "Run", "name": "Rodaje", "moving_time": 3000, "icu_training_load": 45,
                                          "icu_hr_zone_times": [600, 1800, 600, 0, 0, 0, 0], "icu_rpe": 4, "session_rpe": 200}])
    if path.endswith("/events"):
        return httpx.Response(200, json=[{"category": "RACE_A", "name": "Maratón de Valencia", "type": "Run", "distance": 42195,
                                          "start_date_local": f"{(TODAY + timedelta(weeks=10)).isoformat()}T00:00:00"}])
    return httpx.Response(200, json={"icu_resting_hr": 46, "sportSettings": [{"types": ["Run"], "lthr": 174, "max_hr": 192}]})


def test_endpoints(monkeypatch):
    h = linked_user(monkeypatch, fake_intervals)

    state = api.get("/state", headers=h).json()
    assert state["tsb"] == -5 and state["hrv_status"] == "outside_swc" and state["physiology"]["lthr"] == 174
    assert state["goal"]["name"] == "Maratón de Valencia" and state["last_activity"]["gray_zone"]
    assert state["goal"]["distance_km"] == 42.195 and state["srpe_week"]["load"] == 200

    today = api.get("/plan/today", headers=h).json()
    if today["main"]:  # HRV fuera de rango + sueño 45: recuperación
        assert today["main"]["type"] == "recovery"
        assert today["strength"] is None

    assert len(api.get("/plan?days=14", headers=h).json()["days"]) == 14


def test_send_today(monkeypatch):
    sent = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            sent.append(request)
            return httpx.Response(200, json=[{"id": 99, **json.loads(request.content)[0]}])
        return fake_intervals(request)

    h = linked_user(monkeypatch, handler)
    r = api.post("/plan/today/send", headers=h)
    if r.status_code == 409:  # el día de la semana del test es de descanso
        assert not sent
        return
    assert r.status_code == 200 and r.json()["id"] == 99
    req = sent[0]
    assert req.url.path.endswith("/events/bulk") and req.url.params["upsert"] == "true"
    body = json.loads(req.content)[0]
    assert body["external_id"] == f"trainsync-{TODAY.isoformat()}" and body["category"] == "WORKOUT"
    assert "- " in body["description"]


def test_implausible_lthr_is_replaced_and_flagged():
    athlete = {"sportSettings": [
        {"types": ["Ride"], "lthr": 174, "max_hr": 192},
        {"types": ["Run", "TrailRun"], "lthr": 190, "max_hr": 192},  # caso real: 99 % de la FC máx
    ]}
    profile = plan_router.fill_physiology(plan_router.engine.Profile(active_sports=["running", "cycling"]), athlete)
    assert profile.lthr_for("running") == 174 and profile.lthr_for("cycling") == 174
    assert "running" not in profile.lthr_by_sport and len(profile.warnings) == 1


def test_race_plan_needs_session_and_own_linked_account(monkeypatch):
    """Nunca se usa una cuenta del servidor: sin sesión 401; con sesión pero sin vincular, 409."""
    for path in ("/state", "/plan/today", "/plan"):
        assert api.get(path).status_code == 401
    token = api.post("/auth/register", json={"email": "sin-reloj@x.es", "password": "una-clave-larga",
                                             "birth_year": 1990, "accept_terms": True, "health_consent": True}).json()["token"]
    assert api.get("/state", headers={"Authorization": f"Bearer {token}"}).status_code == 409
    assert api.post("/plan/today/send", headers={"Authorization": f"Bearer {token}"}).status_code == 409


def test_run_model_warns_when_lthr_and_threshold_pace_disagree(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/streams"):
            hr = [140 + i // 70 for i in range(3800)]  # 140-194 ppm, 70 s por ppm, en llano
            return httpx.Response(200, json=[{"type": "heartrate", "data": hr},
                                             {"type": "velocity_smooth", "data": [-0.9 + 0.024 * h for h in hr]},
                                             {"type": "grade_smooth", "data": [0.0] * len(hr)}])
        if request.url.path.endswith("/activities"):
            return httpx.Response(200, json=[{"id": "r1", "type": "Run", "average_heartrate": 165,
                                              "start_date_local": f"{(TODAY - timedelta(3)).isoformat()}T08:00:00"}])
        if request.url.path.rstrip("/").endswith("/i1"):
            return httpx.Response(200, json={"sportSettings": [{"types": ["Run"], "lthr": 174, "max_hr": 192,
                                                                "threshold_pace": 1000 / 281}]})
        return fake_intervals(request)

    h = linked_user(monkeypatch, handler)
    plan_router._SAMPLES.clear()
    state = api.get("/state", headers=h).json()
    # 174 ppm → ~5:06/km con esta recta (punto medio de cada tramo de 5 ppm) frente a 4:41/km: más de un 5 %
    assert any("5:06/km" in w and "4:41/km" in w for w in state["warnings"]), state["warnings"]


def test_race_plan_follows_the_user_profile(monkeypatch):
    h = linked_user(monkeypatch, fake_intervals)
    days = {"TUE": 45, "THU": 45, "SAT": 60, "SUN": 90}
    profile = {"goal": "fitness", "days": days, "strength": "none", "cardio": ["walking", "cycling"]}
    assert api.put("/me/profile", json=profile, headers=h).status_code == 204
    plan = api.get("/plan?days=14", headers=h).json()
    for d in plan["days"]:
        if d["weekday"] not in days:
            assert d["main"] is None  # días sin disponibilidad = descanso
        elif d["main"]:
            assert d["main"]["sport"] == "cycling" and d["main"]["duration_min"] <= days[d["weekday"]]
            assert d["strength"] is None and all(a["sport"] == "cycling" for a in d["alternatives"])
    only_walking = profile | {"cardio": ["walking"]}
    api.put("/me/profile", json=only_walking, headers=h)
    assert api.get("/plan", headers=h).status_code == 409


def test_pick_goal_prefers_priority_then_nearest():
    today = date(2026, 10, 21)
    g = lambda name, days, prio: plan_router.engine.Goal(name=name, sport="running", date=today + timedelta(days), priority=prio)
    goals = [g("B cercana", 20, "B"), g("A lejana", 200, "A"), g("A cercana", 100, "A"), g("pasada", -5, "A")]
    assert plan_router.pick_goal(goals, today).name == "A cercana"
    assert plan_router.pick_goal([g("pasada", -1, "A")], today) is None


def test_goal_created_in_the_app_drives_the_race_plan(monkeypatch):
    def no_races(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/events"):
            return httpx.Response(200, json=[])  # nada en el calendario de intervals.icu
        return fake_intervals(request)

    h = linked_user(monkeypatch, no_races)
    assert api.get("/state", headers=h).json()["goal"] is None
    race = {"name": "Medio maratón de Alicante", "sport": "running", "date": (TODAY + timedelta(weeks=12)).isoformat(),
            "priority": "A", "distance_km": 21.1}
    r = api.post("/me/goals", json=race, headers=h)
    assert r.status_code == 201
    state = api.get("/state", headers=h).json()
    assert state["goal"]["name"] == "Medio maratón de Alicante" and state["goal"]["distance_km"] == 21.1
    assert api.post("/me/goals", json=race | {"date": (TODAY - timedelta(1)).isoformat()}, headers=h).status_code == 422
    other = linked_user(monkeypatch, no_races)
    assert api.delete(f"/me/goals/{r.json()['id']}", headers=other).status_code == 404  # no es suyo
    assert api.delete(f"/me/goals/{r.json()['id']}", headers=h).status_code == 204
    assert api.get("/me/goals", headers=h).json() == []
