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
    done = {d["date"]: [{"sport": s["sport"], "source": "manual", "minutes": None} for s in d["sessions"]]
            for d in planned[: len(planned) // 2]}  # la mitad de los días
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


# ---- intervals.icu por usuario (clave cifrada) y envío del plan de salud

import base64  # noqa: E402
import json  # noqa: E402
import os  # noqa: E402

import httpx  # noqa: E402
from sqlalchemy import select  # noqa: E402

from app import crypto, db  # noqa: E402
from app import links  # noqa: E402
from app.routers import me as me_router  # noqa: E402
from app.services.intervals_client import IntervalsClient  # noqa: E402


def test_crypto_roundtrip_and_requires_a_key(monkeypatch):
    monkeypatch.setenv("ENCRYPTION_KEY", base64.b64encode(os.urandom(32)).decode())
    token = crypto.encrypt("clave-secreta")
    assert token != crypto.encrypt("clave-secreta") and crypto.decrypt(token) == "clave-secreta"  # nonce aleatorio
    monkeypatch.delenv("ENCRYPTION_KEY")
    try:
        crypto.encrypt("x")
        raise AssertionError("sin ENCRYPTION_KEY no debe cifrar")
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 503


def test_link_intervals_and_send_today(monkeypatch):
    monkeypatch.setenv("ENCRYPTION_KEY", base64.b64encode(os.urandom(32)).decode())
    posted = []

    def fake(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/activities"):  # una caminata de 30 min hoy y un paseo de 5 que no cuenta
            return httpx.Response(200, json=[
                {"type": "Walk", "moving_time": 1800, "start_date_local": f"{date.today().isoformat()}T08:00:00"},
                {"type": "Walk", "moving_time": 300, "start_date_local": f"{(date.today() - timedelta(1)).isoformat()}T08:00:00"}])
        if request.method == "POST":
            events = json.loads(request.content)
            posted.extend(events)
            return httpx.Response(200, json=[{"id": 100 + i, **e} for i, e in enumerate(events)])
        return httpx.Response(200, json={"icu_resting_hr": 50, "sportSettings": [
            {"types": ["Run"], "lthr": 170, "max_hr": 190}, {"types": ["Ride"], "lthr": 165, "max_hr": 190}]})

    monkeypatch.setattr(links, "make_client", lambda a, k: IntervalsClient(a, k, httpx.MockTransport(fake)))
    h = auth("reloj@x.es")
    every_day = {d: 60 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")}
    api.put("/me/profile", json=PROFILE | {"days": every_day, "cardio": ["walking", "cycling"]}, headers=h)
    assert api.post("/me/plan/today/send", headers=h).status_code == 409  # sin vincular
    assert api.put("/me/intervals", json={"athlete_id": "i123", "api_key": "clave-de-pruebas-1234"},
                   headers=h).status_code == 204
    plan = api.get("/me/plan", headers=h).json()
    days = {d["date"]: d for d in plan["days"]}
    today = days[date.today().isoformat()]  # detectada sin pulsar nada; cuenta o es extra según lo que toque hoy
    assert [m["why"].startswith("Caminata de 30 min a las 08:00") for m in today["matches"]] == [True]
    assert today["done_from_watch"] == [m["matched"] for m in today["matches"] if m["matched"]]
    yesterday = (date.today() - timedelta(1)).isoformat()
    assert yesterday not in days or days[yesterday]["done_from_watch"] == []  # 5 minutos no cuentan
    with db.SessionLocal() as s:  # la clave no se guarda en claro
        stored = s.get(db.IntervalsLink, s.scalar(select(db.User.id).where(db.User.email == "reloj@x.es")))
        assert "clave-de-pruebas" not in stored.api_key_encrypted
    r = api.post("/me/plan/today/send", headers=h)
    if r.status_code == 409:  # hoy es domingo: día de descanso
        return
    assert r.status_code == 200 and posted
    assert all(e["external_id"].startswith(f"trainsync-{date.today().isoformat()}-") for e in posted)
    assert {e["type"] for e in posted} <= {"WeightTraining", "Walk", "Ride"}
    assert "clave-de-pruebas" not in json.dumps(api.get("/me/export", headers=h).json())


def test_move_and_change_days_this_week():
    h = auth("semana@x.es")
    every_day = {d: 45 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")}
    api.put("/me/profile", json=PROFILE | {"days": every_day}, headers=h)
    today = date.today()
    sunday = today + timedelta(6 - today.weekday())
    assert api.post("/me/week/day", json={"date": (today - timedelta(1)).isoformat(), "minutes": 0},
                    headers=h).status_code == 422  # pasado
    assert api.post("/me/week/day", json={"date": (sunday + timedelta(1)).isoformat(), "minutes": 0},
                    headers=h).status_code == 422  # otra semana
    assert api.post("/me/week/day", json={"date": today.isoformat(), "minutes": 0}, headers=h).status_code == 204
    plan = api.get("/me/plan", headers=h).json()
    assert plan["changed"] and not next(d for d in plan["days"] if d["date"] == today.isoformat())["sessions"]
    if today != sunday:
        r = api.post("/me/week/move", json={"from_date": sunday.isoformat(), "to_date": today.isoformat()}, headers=h)
        assert r.status_code == 204
        assert api.post("/me/week/move", json={"from_date": sunday.isoformat(), "to_date": today.isoformat()},
                        headers=h).status_code == 422  # el domingo ya quedó libre
    assert api.delete("/me/week", headers=h).status_code == 204
    assert api.get("/me/plan", headers=h).json()["changed"] is False


def test_watch_detection_never_breaks_the_plan():
    class Down:
        def activities(self, *_):
            raise httpx.ConnectError("sin red")
    assert me_router.watch_sessions(Down(), date.today()) == {}


def test_forgot_and_reset_password(monkeypatch):
    from app import mail
    sent = {}
    monkeypatch.setattr(mail, "send", lambda to, subject, body: sent.update({to: body}))
    old = auth("olvido@x.es")
    assert api.post("/auth/forgot", json={"email": "nadie@x.es"}).status_code == 202  # no revela si existe
    assert api.post("/auth/forgot", json={"email": "Olvido@x.es"}).status_code == 202
    token = sent["olvido@x.es"].split("token=")[1].split()[0]
    assert api.post("/auth/reset", json={"token": token, "password": "corta"}).status_code == 422
    assert api.post("/auth/reset", json={"token": token, "password": "otra-clave-larga"}).status_code == 200
    assert api.post("/auth/reset", json={"token": token, "password": "otra-clave-mas"}).status_code == 401  # un solo uso
    assert api.get("/me", headers=old).status_code == 401  # la sesión anterior se cierra
    assert api.post("/auth/login", json={"email": "olvido@x.es", "password": "otra-clave-larga"}).status_code == 200
    assert api.get("/me", headers=old | {"Authorization": f"Bearer {token}"}).status_code == 401  # no sirve de sesión


def test_email_verification(monkeypatch):
    from app import mail
    sent = {}
    monkeypatch.setattr(mail, "send", lambda to, subject, body: sent.update({to: body}))
    h = auth("verifica@x.es")
    assert api.get("/me", headers=h).json() == {"email": "verifica@x.es", "email_verified": False}
    token = sent["verifica@x.es"].split("token=")[1].split()[0]
    assert api.post("/auth/verify", json={"token": "basura"}).status_code == 401
    assert api.post("/auth/verify", json={"token": token}).status_code == 204
    assert api.get("/me", headers=h).json()["email_verified"] is True
    assert api.get("/me/export", headers=h).json()["account"]["email_verified_at"]


def test_login_attempts_are_limited():
    register("fuerza@x.es")
    for _ in range(10):
        assert api.post("/auth/login", json={"email": "fuerza@x.es", "password": "mala-clave-123"}).status_code == 401
    blocked = api.post("/auth/login", json={"email": "fuerza@x.es", "password": "una-clave-larga"})
    assert blocked.status_code == 429 and int(blocked.headers["Retry-After"]) > 0


def test_saved_fat_loss_profiles_fall_back_to_fitness():
    h = auth("grasa@x.es")
    assert api.put("/me/profile", json=PROFILE | {"goal": "fat_loss"}, headers=h).status_code == 422  # ya no se ofrece
    api.put("/me/profile", json=PROFILE, headers=h)
    with db.SessionLocal() as s:  # perfil guardado antes de retirarlo
        row = s.get(db.HealthProfileRow, s.scalar(select(db.User.id).where(db.User.email == "grasa@x.es")))
        row.plan = row.plan | {"goal": "fat_loss"}
        s.commit()
    assert api.get("/me/plan", headers=h).json()["goal"] == "fitness"
