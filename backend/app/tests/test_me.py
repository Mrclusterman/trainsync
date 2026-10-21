"""Cuenta, perfil, historial y derechos RGPD (Fase 1)."""
from datetime import date, timedelta

from fastapi.testclient import TestClient

from app.main import app
from app.services import health_plan as hp

api = TestClient(app)
PROFILE = {"goal": "fitness", "days": {"MON": 60, "WED": 60, "FRI": 60, "SAT": 90}, "cardio": ["walking"],
           "screening": {"active": False, "known_disease": False, "symptoms": False}}


def register(email: str, **kw) -> dict:
    body = {"email": email, "password": "una-clave-larga", "birth_year": 1990, "accept_terms": True,
            "health_consent": True} | kw
    return api.post("/auth/register", json=body)


def auth(email: str) -> dict:
    return {"Authorization": f"Bearer {register(email).json()['token']}"}


def test_register_requires_separate_health_consent_and_minimum_age():
    assert register("a@x.es", health_consent=False).status_code == 422
    assert register("b@x.es", birth_year=date.today().year - 13).status_code == 422
    assert register("c@x.es").status_code == 201
    assert register("C@x.es").status_code == 409  # el email no distingue mayúsculas


def test_login_and_token():
    register("login@x.es")
    assert api.post("/auth/login", json={"email": "login@x.es", "password": "mala"}).status_code == 401
    token = api.post("/auth/login", json={"email": "LOGIN@x.es", "password": "una-clave-larga"}).json()["token"]
    assert api.get("/me/plan", headers={"Authorization": f"Bearer {token}"}).status_code == 404  # sin perfil
    assert api.get("/me/plan").status_code == 401
    assert api.get("/me/plan", headers={"Authorization": "Bearer basura"}).status_code == 401


def test_profile_plan_and_adherence_from_logged_sessions():
    h = auth("plan@x.es")
    assert api.put("/me/profile", json=PROFILE, headers=h).status_code == 204
    plan = api.get("/me/plan", headers=h).json()
    assert plan["block"] == 0 and plan["adherence"] == [] and len(plan["days"]) == 7
    assert api.post("/me/sessions", json={"date": (date.today() + timedelta(1)).isoformat(), "sport": "walking"},
                    headers=h).status_code == 422  # no se registran sesiones futuras


def test_adherence_history_counts_done_over_planned():
    p = hp.HealthProfile("fitness", {"MON": 60, "WED": 60, "FRI": 60, "SAT": 90})
    start = date(2026, 1, 5)
    first_block = [hp.week_plan(p, start + timedelta(weeks=w), [], w) for w in range(4)]
    planned = [d for wk in first_block for d in wk["days"] if d["sessions"]]
    done = {d["date"]: len(d["sessions"]) for d in planned[: len(planned) // 2]}  # la mitad de los días
    history = hp.adherence_history(p, start, done, 1)
    assert 0.3 < history[0] < 0.7
    assert hp.adherence_history(p, start, {}, 2)[0] == 0.0
    assert hp.position(start, start + timedelta(weeks=5)) == (1, 1)


def test_readiness_export_and_delete():
    h = auth("borrar@x.es")
    api.put("/me/profile", json=PROFILE, headers=h)
    assert api.post("/me/readiness", json={"sleep": 1, "energy": 1, "soreness": 1}, headers=h).status_code == 204
    assert api.post("/me/sessions", json={"date": date.today().isoformat(), "sport": "walking", "rpe": 4},
                    headers=h).status_code == 201
    data = api.get("/me/export", headers=h).json()
    assert data["account"]["email"] == "borrar@x.es" and len(data["sessions"]) == 1 and len(data["readiness"]) == 1
    assert "password_hash" not in str(data)
    assert api.delete("/me", headers=h).status_code == 204
    assert api.get("/me/export", headers=h).status_code == 401  # la cuenta ya no existe
    assert api.post("/auth/login", json={"email": "borrar@x.es", "password": "una-clave-larga"}).status_code == 401
