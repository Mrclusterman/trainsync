"""API completa contra un intervals.icu simulado (httpx.MockTransport)."""
import json
from datetime import date, timedelta

import httpx
from fastapi.testclient import TestClient

from app.main import app
from app.routers import plan as plan_router
from app.services.intervals_client import IntervalsClient

TODAY = date.today()


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
    monkeypatch.setattr(plan_router, "client", lambda: IntervalsClient("i1", "k", httpx.MockTransport(fake_intervals)))
    api = TestClient(app)

    state = api.get("/state").json()
    assert state["tsb"] == -5 and state["hrv_status"] == "outside_swc" and state["physiology"]["lthr"] == 174
    assert state["goal"]["name"] == "Maratón de Valencia" and state["last_activity"]["gray_zone"]
    assert state["goal"]["distance_km"] == 42.195 and state["srpe_week"]["load"] == 200

    today = api.get("/plan/today").json()
    if today["main"]:  # HRV fuera de rango + sueño 45: recuperación
        assert today["main"]["type"] == "recovery"
        assert today["strength"] is None

    assert len(api.get("/plan?days=14").json()["days"]) == 14


def test_send_today(monkeypatch):
    sent = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            sent.append(request)
            return httpx.Response(200, json=[{"id": 99, **json.loads(request.content)[0]}])
        return fake_intervals(request)

    monkeypatch.setattr(plan_router, "client", lambda: IntervalsClient("i1", "k", httpx.MockTransport(handler)))
    r = TestClient(app).post("/plan/today/send")
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
    profile = plan_router.fill_physiology(plan_router.config.load_profile(), athlete)
    assert profile.lthr_for("running") == 174 and profile.lthr_for("cycling") == 174
    assert "running" not in profile.lthr_by_sport and len(profile.warnings) == 1


def test_missing_credentials_returns_503(monkeypatch):
    monkeypatch.setattr(plan_router.config, "INTERVALS_API_KEY", "")
    plan_router.client.cache_clear()
    assert TestClient(app).get("/plan/today").status_code == 503


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

    monkeypatch.setattr(plan_router, "client", lambda: IntervalsClient("i1", "k", httpx.MockTransport(handler)))
    plan_router.run_samples.cache_clear()
    state = TestClient(app).get("/state").json()
    # 174 ppm → ~5:06/km con esta recta (punto medio de cada tramo de 5 ppm) frente a 4:41/km: más de un 5 %
    assert any("5:06/km" in w and "4:41/km" in w for w in state["warnings"]), state["warnings"]
