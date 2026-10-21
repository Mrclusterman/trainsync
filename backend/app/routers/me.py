"""Cuenta, perfil e historial del usuario (Fase 1, CUMPLIMIENTO #6, #17 y #20)."""
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app import auth, db, schemas
from app.routers.health import build_plan, profile_from
from app.services import health_plan as hp

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
    plan, screening = req.model_dump(exclude={"screening"}), req.screening.model_dump()
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


def user_plan(user: db.User, session: Session, monday: date) -> dict:
    row = session.get(db.HealthProfileRow, user.id)
    if row is None:
        raise HTTPException(404, "Primero completa tu perfil (PUT /me/profile)")
    req = schemas.HealthPlanRequest(**row.plan, age=date.today().year - user.birth_year, screening=row.screening)
    block, week = hp.position(row.block_start, monday)
    logs = session.scalars(select(db.SessionLog).where(db.SessionLog.user_id == user.id)).all()
    done: dict[str, int] = {}
    for log in logs:
        done[log.date.isoformat()] = done.get(log.date.isoformat(), 0) + 1
    adherence = hp.adherence_history(profile_from(req)[0], row.block_start, done, block)
    ready = session.get(db.ReadinessLog, (user.id, date.today()))
    readiness = schemas.Readiness(sleep=ready.sleep, energy=ready.energy, soreness=ready.soreness) if ready else None
    return build_plan(req, monday, adherence, week, readiness) | {"block": block, "adherence": adherence}


@router.get("/me/plan", response_model=schemas.UserPlan)
def get_plan(start: date | None = None, user: db.User = Depends(auth.current_user),
             session: Session = Depends(db.get_session)):
    today = date.today()
    monday = (start or today) - timedelta((start or today).weekday())
    return user_plan(user, session, monday)


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
    return {"account": {"email": user.email, "birth_year": user.birth_year, "created_at": user.created_at.isoformat(),
                        "health_consent_at": user.health_consent_at.isoformat(), "terms_version": user.terms_version},
            "profile": {"plan": row.plan, "screening": row.screening, "block_start": row.block_start.isoformat()} if row else None,
            "sessions": [{"date": x.date.isoformat(), "sport": x.sport, "rpe": x.rpe} for x in logs],
            "readiness": [{"date": x.date.isoformat(), "sleep": x.sleep, "energy": x.energy, "soreness": x.soreness}
                          for x in ready]}


@router.delete("/me", status_code=204)
def delete_account(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Borrado inmediato de la cuenta y de todos sus datos (RGPD art. 17, CUMPLIMIENTO #20)."""
    for model in (db.SessionLog, db.ReadinessLog, db.HealthProfileRow):
        session.execute(delete(model).where(model.user_id == user.id))
    session.delete(session.get(db.User, user.id))
    session.commit()
    return Response(status_code=204)
