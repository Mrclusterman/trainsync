"""Cuenta, perfil e historial del usuario (Fase 1, CUMPLIMIENTO #6, #17 y #20)."""
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app import auth, crypto, db, schemas
from app.routers import plan as event_plan
from app.routers.health import build_plan, profile_from
from app.services import health_plan as hp
from app.services import training_engine as engine
from app.services import workout_text
from app.services.intervals_client import IntervalsClient

make_client = IntervalsClient  # se sustituye en los tests

router = APIRouter()
TERMS_VERSION = "2026-10-21"
MIN_AGE = 14  # LOPDGDD art. 7: edad mínima para consentir en España (CUMPLIMIENTO #17)


@router.post("/auth/register", response_model=schemas.Token, status_code=201)
def register(req: schemas.RegisterIn, session: Session = Depends(db.get_session)):
    if not (req.accept_terms and req.health_consent):
        raise HTTPException(422, "Hace falta aceptar los términos y, por separado, el tratamiento de datos de salud")
    if date.today().year - req.birth_year < MIN_AGE:
        raise HTTPException(422, f"Hace falta tener al menos {MIN_AGE} años")
    email = req.email.strip().lower()
    if session.scalar(select(db.User).where(db.User.email == email)):
        raise HTTPException(409, "Ya existe una cuenta con ese email")
    user = db.User(email=email, password_hash=auth.hash_password(req.password), birth_year=req.birth_year,
                   health_consent_at=db.now(), terms_version=TERMS_VERSION)
    session.add(user)
    session.commit()
    return {"token": auth.create_token(user.id)}


@router.post("/auth/login", response_model=schemas.Token)
def login(req: schemas.LoginIn, session: Session = Depends(db.get_session)):
    user = session.scalar(select(db.User).where(db.User.email == req.email.strip().lower()))
    if user is None or not auth.verify_password(req.password, user.password_hash):
        raise HTTPException(401, "Email o contraseña incorrectos")
    return {"token": auth.create_token(user.id)}


@router.put("/me/profile", status_code=204)
def put_profile(req: schemas.ProfileIn, user: db.User = Depends(auth.current_user),
                session: Session = Depends(db.get_session)):
    row = session.get(db.HealthProfileRow, user.id)
    # Sin cribado se guarda {}: la columna es NOT NULL en las bases ya creadas
    plan, screening = req.model_dump(exclude={"screening"}), (req.screening.model_dump() if req.screening else {})
    if row is None:
        today = date.today()
        session.add(db.HealthProfileRow(user_id=user.id, plan=plan, screening=screening,
                                        block_start=today - timedelta(today.weekday())))
    else:
        # Cambiar de objetivo empieza un bloque nuevo; el resto de cambios mantiene el bloque en curso
        if row.plan.get("goal") != plan["goal"]:
            today = date.today()
            row.block_start = today - timedelta(today.weekday())
        row.plan, row.screening = plan, screening
    session.commit()
    return Response(status_code=204)


def linked_client(user: db.User, session: Session) -> IntervalsClient | None:
    link = session.get(db.IntervalsLink, user.id)
    return make_client(link.athlete_id, crypto.decrypt(link.api_key_encrypted)) if link else None


def physiology(client: IntervalsClient) -> engine.Profile:
    """LTHR, FC máx., CSS y ritmo umbral de la cuenta vinculada (mismas reglas que el plan de eventos)."""
    return event_plan.fill_physiology(engine.Profile(active_sports=["running", "cycling"]), client.athlete())


def user_plan(user: db.User, session: Session, monday: date, ephys: engine.Profile | None = None) -> dict:
    row = session.get(db.HealthProfileRow, user.id)
    if row is None:
        raise HTTPException(404, "Primero completa tu perfil (PUT /me/profile)")
    req = schemas.HealthPlanRequest(**row.plan, age=date.today().year - user.birth_year, screening=row.screening or None)
    block, week = hp.position(row.block_start, monday)
    logs = session.scalars(select(db.SessionLog).where(db.SessionLog.user_id == user.id)).all()
    done: dict[str, int] = {}
    done_sports: dict[str, list[str]] = {}
    for log in logs:
        done[log.date.isoformat()] = done.get(log.date.isoformat(), 0) + 1
        done_sports.setdefault(log.date.isoformat(), []).append(log.sport)
    physio = {"lthr": ephys.lthr, "max_hr": ephys.max_hr, "css": ephys.css} if ephys else None
    adherence = hp.adherence_history(profile_from(req, physio)[0], row.block_start, done, block)
    ready = session.get(db.ReadinessLog, (user.id, date.today()))
    readiness = schemas.Readiness(sleep=ready.sleep, energy=ready.energy, soreness=ready.soreness) if ready else None
    plan = build_plan(req, monday, adherence, week, readiness, physio) | {"block": block, "adherence": adherence}
    for day in plan["days"]:
        day["done"] = done_sports.get(day["date"], [])
    return plan


@router.get("/me/plan", response_model=schemas.UserPlan)
def get_plan(start: date | None = None, user: db.User = Depends(auth.current_user),
             session: Session = Depends(db.get_session)):
    today = date.today()
    monday = (start or today) - timedelta((start or today).weekday())
    client = linked_client(user, session)
    return user_plan(user, session, monday, physiology(client) if client else None)


@router.put("/me/intervals", status_code=204)
def link_intervals(req: schemas.IntervalsLinkIn, user: db.User = Depends(auth.current_user),
                   session: Session = Depends(db.get_session)):
    """Vincula intervals.icu una vez: se comprueba la clave y se guarda cifrada."""
    try:
        make_client(req.athlete_id, req.api_key).athlete()
    except Exception:
        raise HTTPException(422, "intervals.icu no acepta ese ID de atleta o esa clave")
    session.merge(db.IntervalsLink(user_id=user.id, athlete_id=req.athlete_id, api_key_encrypted=crypto.encrypt(req.api_key)))
    session.commit()
    return Response(status_code=204)


@router.delete("/me/intervals", status_code=204)
def unlink_intervals(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    session.execute(delete(db.IntervalsLink).where(db.IntervalsLink.user_id == user.id))
    session.commit()
    return Response(status_code=204)


@router.post("/me/plan/today/send", response_model=list[schemas.SentEvent])
def send_today(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Envía las sesiones de hoy al calendario de intervals.icu (resistencia estructurada; fuerza como lista)."""
    client = linked_client(user, session)
    if client is None:
        raise HTTPException(409, "Vincula primero tu cuenta de intervals.icu (PUT /me/intervals)")
    ephys = physiology(client)
    today = date.today()
    plan = user_plan(user, session, today - timedelta(today.weekday()), ephys)
    sessions = next(d["sessions"] for d in plan["days"] if d["date"] == today.isoformat())
    if not sessions:
        raise HTTPException(409, "Hoy no hay sesiones que enviar")
    events = [workout_text.strength_event(s, today.isoformat()) if s["sport"] == "strength"
              else workout_text.event(s, today.isoformat(), ephys, key=s["sport"]) for s in sessions]
    created = client.upsert_workouts(events)
    return [{"id": c["id"], "name": e["name"]} for c, e in zip(created, events)]


@router.post("/me/sessions", status_code=201)
def log_session(req: schemas.SessionLogIn, user: db.User = Depends(auth.current_user),
                session: Session = Depends(db.get_session)):
    if req.date > date.today():
        raise HTTPException(422, "No se puede marcar como hecha una sesión futura")
    exists = session.scalar(select(db.SessionLog).where(db.SessionLog.user_id == user.id, db.SessionLog.date == req.date,
                                                        db.SessionLog.sport == req.sport))
    if exists is None:
        session.add(db.SessionLog(user_id=user.id, date=req.date, sport=req.sport, rpe=req.rpe))
    else:
        exists.rpe = req.rpe
    session.commit()
    return {"ok": True}


@router.post("/me/readiness", status_code=204)
def log_readiness(req: schemas.Readiness, user: db.User = Depends(auth.current_user),
                  session: Session = Depends(db.get_session)):
    session.merge(db.ReadinessLog(user_id=user.id, date=date.today(), **req.model_dump()))
    session.commit()
    return Response(status_code=204)


@router.get("/me/export")
def export(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Portabilidad (RGPD art. 20): todos los datos del usuario en JSON."""
    row = session.get(db.HealthProfileRow, user.id)
    logs = session.scalars(select(db.SessionLog).where(db.SessionLog.user_id == user.id)).all()
    ready = session.scalars(select(db.ReadinessLog).where(db.ReadinessLog.user_id == user.id)).all()
    link = session.get(db.IntervalsLink, user.id)  # la clave no se exporta: es un secreto, no un dato personal
    return {"account": {"email": user.email, "birth_year": user.birth_year, "created_at": user.created_at.isoformat(),
                        "health_consent_at": user.health_consent_at.isoformat(), "terms_version": user.terms_version},
            "profile": {"plan": row.plan, "screening": row.screening, "block_start": row.block_start.isoformat()} if row else None,
            "intervals": {"athlete_id": link.athlete_id, "linked_at": link.linked_at.isoformat()} if link else None,
            "sessions": [{"date": x.date.isoformat(), "sport": x.sport, "rpe": x.rpe} for x in logs],
            "readiness": [{"date": x.date.isoformat(), "sleep": x.sleep, "energy": x.energy, "soreness": x.soreness}
                          for x in ready]}


@router.delete("/me", status_code=204)
def delete_account(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Borrado inmediato de la cuenta y de todos sus datos (RGPD art. 17, CUMPLIMIENTO #20)."""
    for model in (db.SessionLog, db.ReadinessLog, db.HealthProfileRow, db.IntervalsLink):
        session.execute(delete(model).where(model.user_id == user.id))
    session.delete(session.get(db.User, user.id))
    session.commit()
    return Response(status_code=204)
