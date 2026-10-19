"""API completa contra un intervals.icu simulado (httpx.MockTransport)."""
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
                                          "icu_hr_zone_times": [600, 1800, 600, 0, 0, 0, 0]}])
    if path.endswith("/events"):
        return httpx.Response(200, json=[{"category": "RACE_A", "name": "Maratón de Valencia", "type": "Run",
                                          "start_date_local": f"{(TODAY + timedelta(weeks=10)).isoformat()}T00:00:00"}])
    return httpx.Response(200, json={"icu_resting_hr": 46, "sportSettings": [{"types": ["Run"], "lthr": 174, "max_hr": 192}]})


def test_endpoints(monkeypatch):
    monkeypatch.setattr(plan_router, "client", lambda: IntervalsClient("i1", "k", httpx.MockTransport(fake_intervals)))
    api = TestClient(app)

    state = api.get("/state").json()
    assert state["tsb"] == -5 and state["hrv_status"] == "outside_swc" and state["physiology"]["lthr"] == 174
    assert state["goal"]["name"] == "Maratón de Valencia" and state["last_activity"]["gray_zone"]

    today = api.get("/plan/today").json()
    if today["main"]:  # HRV fuera de rango + sueño 45: recuperación
        assert today["main"]["type"] == "recovery"
        assert today["strength"] is None

    assert len(api.get("/plan?days=14").json()["days"]) == 14


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
