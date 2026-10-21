"""Cuenta, perfil e historial del usuario (Fase 1, CUMPLIMIENTO #6, #17 y #20)."""
from datetime import date, timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Response
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app import auth, crypto, db, links, mail, ratelimit, schemas
from app.links import linked_client
from app.routers import plan as event_plan
from app.routers.health import build_plan, profile_from
from app.services import health_plan as hp
from app.services import training_engine as engine
from app.services import workout_text
from app.services.intervals_client import IntervalsClient

router = APIRouter()
TERMS_VERSION = "2026-10-21"
MIN_AGE = 14  # LOPDGDD art. 7: edad mínima para consentir en España (CUMPLIMIENTO #17)


@router.post("/auth/register", response_model=schemas.Token, status_code=201)
def register(req: schemas.RegisterIn, tasks: BackgroundTasks, session: Session = Depends(db.get_session)):
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
    tasks.add_task(mail.verify_email, user.email, auth.create_token(user, "verify"))
    return {"token": auth.create_token(user)}


@router.post("/auth/login", response_model=schemas.Token)
def login(req: schemas.LoginIn, session: Session = Depends(db.get_session)):
    email = req.email.strip().lower()
    ratelimit.check(f"login:{email}", 10, 15 * 60)  # [Práctica: OWASP, limitar intentos por cuenta]
    user = session.scalar(select(db.User).where(db.User.email == email))
    if user is None or not auth.verify_password(req.password, user.password_hash):
        raise HTTPException(401, "Email o contraseña incorrectos")
    return {"token": auth.create_token(user)}


@router.post("/auth/forgot", status_code=202)
def forgot_password(req: schemas.ForgotIn, tasks: BackgroundTasks, session: Session = Depends(db.get_session)):
    """Siempre 202 y el email sale en segundo plano: la respuesta no revela si la cuenta existe."""
    email = req.email.strip().lower()
    ratelimit.check(f"forgot:{email}", 3, 60 * 60)
    user = session.scalar(select(db.User).where(db.User.email == email))
    if user:
        tasks.add_task(mail.reset_password, user.email, auth.create_token(user, "reset"))
    return Response(status_code=202)


@router.post("/auth/reset", response_model=schemas.Token)
def reset_password(req: schemas.ResetIn, session: Session = Depends(db.get_session)):
    """Nueva contraseña con el enlace del email; cierra las demás sesiones (auth.password_stamp)."""
    user = auth.user_from(req.token, "reset", session)
    user.password_hash = auth.hash_password(req.password)
    session.merge(db.EmailVerification(user_id=user.id))  # quien abre el enlace recibe los emails de la cuenta
    session.commit()
    return {"token": auth.create_token(user)}


@router.post("/auth/verify", status_code=204)
def verify_email(req: schemas.LinkTokenIn, session: Session = Depends(db.get_session)):
    user = auth.user_from(req.token, "verify", session)
    if session.get(db.EmailVerification, user.id) is None:
        session.add(db.EmailVerification(user_id=user.id))
        session.commit()
    return Response(status_code=204)


@router.get("/me", response_model=schemas.Account)
def account(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    return {"email": user.email, "email_verified": session.get(db.EmailVerification, user.id) is not None}


@router.post("/me/verify", status_code=202)
def resend_verification(tasks: BackgroundTasks, user: db.User = Depends(auth.current_user)):
    ratelimit.check(f"verify:{user.id}", 3, 60 * 60)
    tasks.add_task(mail.verify_email, user.email, auth.create_token(user, "verify"))
    return Response(status_code=202)


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


def physiology(client: IntervalsClient) -> engine.Profile:
    """LTHR, FC máx., CSS y ritmo umbral de la cuenta vinculada (mismas reglas que el plan de eventos)."""
    return event_plan.fill_physiology(engine.Profile(active_sports=["running", "cycling"]), client.athlete())


# Tipos de actividad de intervals.icu → deportes del plan de salud, para detectar sesiones hechas sin pulsar nada
ACTIVITY_SPORTS = {"Run": "running", "TrailRun": "running", "VirtualRun": "running", "Ride": "cycling",
                   "VirtualRide": "cycling", "GravelRide": "cycling", "MountainBikeRide": "cycling", "EBikeRide": "cycling",
                   "Swim": "swimming_pool", "OpenWaterSwim": "swimming_pool", "Walk": "walking", "Hike": "walking",
                   "WeightTraining": "strength"}
MIN_ACTIVITY_S = 600  # menos de 10 min no cuenta como sesión [Inferencia]


def watch_sessions(client: IntervalsClient, oldest: date) -> dict[str, list[dict]]:
    """Actividades de cada día según el reloj, para hp.match_day; vacío si intervals.icu falla (queda lo marcado a mano)."""
    try:
        activities = client.activities(oldest, date.today())
    except Exception:  # red o intervals.icu caído: no debe tumbar el plan
        return {}
    if not isinstance(activities, list):  # respuesta inesperada: mejor no detectar nada que inventar
        return {}
    done: dict[str, list[dict]] = {}
    for a in activities:
        sport = ACTIVITY_SPORTS.get(a.get("type"))
        start = a.get("start_date_local") or ""
        if sport and (a.get("moving_time") or 0) >= MIN_ACTIVITY_S and start:
            done.setdefault(start[:10], []).append({"sport": sport, "source": "watch", "minutes": a["moving_time"] // 60,
                                                    "start": start[11:16] or None})
    return done


def user_plan(user: db.User, session: Session, monday: date, ephys: engine.Profile | None = None,
              client: IntervalsClient | None = None) -> dict:
    row = session.get(db.HealthProfileRow, user.id)
    if row is None:
        raise HTTPException(404, "Primero completa tu perfil (PUT /me/profile)")
    # Perfiles guardados con "perder grasa" (retirado el 2026-10-21) siguen con "ponerse en forma"
    saved = row.plan | ({"goal": "fitness"} if row.plan.get("goal") == "fat_loss" else {})
    req = schemas.HealthPlanRequest(**saved, age=date.today().year - user.birth_year, screening=row.screening or None)
    block, week = hp.position(row.block_start, monday)
    logs = session.scalars(select(db.SessionLog).where(db.SessionLog.user_id == user.id)).all()
    watch = watch_sessions(client, row.block_start) if client else {}
    done = watch
    for log in logs:
        done.setdefault(log.date.isoformat(), []).append({"sport": log.sport, "source": "manual", "minutes": None,
                                                          "start": None})
    physio = {"lthr": ephys.lthr, "max_hr": ephys.max_hr, "css": ephys.css} if ephys else None
    changes = {c.week_start.isoformat(): (c.changes, c.since) for c in
               session.scalars(select(db.WeekChange).where(db.WeekChange.user_id == user.id))}
    adherence = hp.adherence_history(profile_from(req, physio)[0], row.block_start, done, block, changes)
    ready = session.get(db.ReadinessLog, (user.id, date.today()))
    readiness = schemas.Readiness(sleep=ready.sleep, energy=ready.energy, soreness=ready.soreness) if ready else None
    plan = build_plan(req, monday, adherence, week, readiness, physio, *changes.get(monday.isoformat(), (None, None)))
    plan |= {"block": block, "adherence": adherence}
    p = profile_from(req, physio)[0]
    for day in plan["days"]:
        for s in day["sessions"]:
            if s["sport"] != "strength":
                s["alternatives"] = hp.alternatives(p, s, monday.isocalendar().week)
        day["matches"] = hp.match_day(day["sessions"], done.get(day["date"], []))
        day["done"] = sorted(m["matched"] for m in day["matches"] if m["matched"])
        day["done_from_watch"] = sorted(m["matched"] for m in day["matches"] if m["matched"] and m["source"] == "watch")
    return plan


@router.get("/me/plan", response_model=schemas.UserPlan)
def get_plan(start: date | None = None, user: db.User = Depends(auth.current_user),
             session: Session = Depends(db.get_session)):
    today = date.today()
    monday = (start or today) - timedelta((start or today).weekday())
    client = linked_client(user, session)
    return user_plan(user, session, monday, physiology(client) if client else None, client)


def check_this_week(day: date) -> date:
    """Solo se pueden cambiar los días que quedan de la semana en curso. Devuelve su lunes."""
    today = date.today()
    monday = today - timedelta(today.weekday())
    if not today <= day <= monday + timedelta(6):
        raise HTTPException(422, "Solo se pueden cambiar los días que quedan de esta semana")
    return monday


def this_week_change(user: db.User, session: Session, day: date) -> db.WeekChange:
    monday = check_this_week(day)
    today = date.today()
    row = session.get(db.WeekChange, (user.id, monday))
    if row is None:
        row = db.WeekChange(user_id=user.id, week_start=monday, changes={}, since=today)
        session.add(row)
    return row


def available_minutes(user: db.User, session: Session, row: db.WeekChange) -> dict[str, int]:
    profile = session.get(db.HealthProfileRow, user.id)
    if profile is None:
        raise HTTPException(404, "Primero completa tu perfil (PUT /me/profile)")
    return profile.plan["days"] | row.changes


@router.post("/me/week/day", status_code=204)
def change_day(req: schemas.DayChangeIn, user: db.User = Depends(auth.current_user),
               session: Session = Depends(db.get_session)):
    """"Hoy no puedo" (minutes = 0) o "puedo entrenar este día": la semana se recalcula desde hoy."""
    row = this_week_change(user, session, req.date)
    available_minutes(user, session, row)
    row.changes = row.changes | {hp.WEEKDAYS[req.date.weekday()]: req.minutes}
    row.since = date.today()
    session.commit()
    return Response(status_code=204)


@router.post("/me/week/move", status_code=204)
def move_session(req: schemas.MoveIn, user: db.User = Depends(auth.current_user),
                 session: Session = Depends(db.get_session)):
    """Mover el entrenamiento de un día a otro de esta semana: el día de origen queda libre y el de destino recibe sus
    minutos (o los suyos, si eran más); el resto de la semana se recalcula."""
    if req.from_date == req.to_date:
        raise HTTPException(422, "Elige otro día")
    check_this_week(req.to_date)
    row = this_week_change(user, session, req.from_date)
    minutes = available_minutes(user, session, row)
    src, dst = hp.WEEKDAYS[req.from_date.weekday()], hp.WEEKDAYS[req.to_date.weekday()]
    if not minutes.get(src):
        raise HTTPException(422, "Ese día no tiene entrenamiento que mover")
    row.changes = row.changes | {dst: max(minutes.get(dst, 0), minutes[src]), src: 0}
    row.since = date.today()
    session.commit()
    return Response(status_code=204)


@router.delete("/me/week", status_code=204)
def reset_week(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Vuelve al plan original de esta semana (los días ya pasados no cambian)."""
    today = date.today()
    session.execute(delete(db.WeekChange).where(db.WeekChange.user_id == user.id,
                                                db.WeekChange.week_start == today - timedelta(today.weekday())))
    session.commit()
    return Response(status_code=204)


GOAL_TYPES = {"running": "Run", "cycling": "Ride", "swimming_pool": "Swim", "swimming_open": "OpenWaterSwim",
              "triathlon": "Ride", "duathlon": "Run"}  # intervals.icu no tiene tipo triatlón/duatlón


def goal_event(g: db.RaceGoal) -> dict:
    """La carrera como evento del calendario de intervals.icu (llega al reloj por su sincronización)."""
    sport_type = "TrailRun" if g.trail and g.sport == "running" else GOAL_TYPES[g.sport]
    event = {"category": f"RACE_{g.priority}", "start_date_local": f"{g.date.isoformat()}T00:00:00", "type": sport_type,
             "name": g.name, "external_id": f"trainsync-goal-{g.id}"}
    return event | ({"distance": round(g.distance_km * 1000)} if g.distance_km else {})


def push_goals(client: IntervalsClient | None, goals: list[db.RaceGoal]) -> bool:
    """Copia (o actualiza) las carreras en intervals.icu. False si no hay cuenta vinculada o intervals.icu falla."""
    if client is None or not goals:
        return False
    try:
        client.upsert_workouts([goal_event(g) for g in goals])
        return True
    except Exception:  # la carrera queda guardada en la app aunque intervals.icu no responda
        return False


def remove_goal_event(client: IntervalsClient | None, g: db.RaceGoal) -> None:
    if client is None:
        return
    try:
        for event in client.events(g.date, g.date):
            if event.get("external_id") == f"trainsync-goal-{g.id}":
                client.delete_event(event["id"])
    except Exception:  # si falla, la copia queda en intervals.icu y se puede borrar allí
        pass


@router.get("/me/goals", response_model=list[schemas.RaceGoalOut])
def list_goals(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    rows = session.scalars(select(db.RaceGoal).where(db.RaceGoal.user_id == user.id).order_by(db.RaceGoal.date)).all()
    return [schemas.RaceGoalOut.model_validate(r, from_attributes=True) for r in rows]


@router.post("/me/goals", response_model=schemas.RaceGoalOut, status_code=201)
def create_goal(req: schemas.RaceGoalIn, user: db.User = Depends(auth.current_user),
                session: Session = Depends(db.get_session)):
    """Carrera objetivo creada en la app: el plan de carrera la tiene en cuenta igual que las de intervals.icu."""
    if not date.today() <= req.date <= date.today() + timedelta(days=730):
        raise HTTPException(422, "La fecha tiene que estar entre hoy y dentro de dos años")
    row = db.RaceGoal(user_id=user.id, **req.model_dump())
    session.add(row)
    session.commit()
    synced = push_goals(linked_client(user, session), [row])
    return schemas.RaceGoalOut.model_validate(row, from_attributes=True).model_copy(update={"synced": synced})


@router.put("/me/goals/{goal_id}", response_model=schemas.RaceGoalOut)
def update_goal(goal_id: int, req: schemas.RaceGoalIn, user: db.User = Depends(auth.current_user),
                session: Session = Depends(db.get_session)):
    """Editar una carrera; su copia en intervals.icu se actualiza (mismo external_id: se mueve, no se duplica)."""
    row = session.get(db.RaceGoal, goal_id)
    if row is None or row.user_id != user.id:  # nunca se edita el objetivo de otro usuario
        raise HTTPException(404, "No existe ese objetivo")
    if not date.today() <= req.date <= date.today() + timedelta(days=730):
        raise HTTPException(422, "La fecha tiene que estar entre hoy y dentro de dos años")
    for field, value in req.model_dump().items():
        setattr(row, field, value)
    session.commit()
    synced = push_goals(linked_client(user, session), [row])
    return schemas.RaceGoalOut.model_validate(row, from_attributes=True).model_copy(update={"synced": synced})


@router.delete("/me/goals/{goal_id}", status_code=204)
def delete_goal(goal_id: int, user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    row = session.get(db.RaceGoal, goal_id)
    if row is None or row.user_id != user.id:  # nunca se borra el objetivo de otro usuario
        raise HTTPException(404, "No existe ese objetivo")
    remove_goal_event(linked_client(user, session), row)
    session.delete(row)
    session.commit()
    return Response(status_code=204)


@router.put("/me/intervals", status_code=204)
def link_intervals(req: schemas.IntervalsLinkIn, user: db.User = Depends(auth.current_user),
                   session: Session = Depends(db.get_session)):
    """Vincula intervals.icu una vez: se comprueba la clave y se guarda cifrada."""
    try:
        links.make_client(req.athlete_id, req.api_key).athlete()
    except Exception:
        raise HTTPException(422, "intervals.icu no acepta ese ID de atleta o esa clave")
    session.merge(db.IntervalsLink(user_id=user.id, athlete_id=req.athlete_id, api_key_encrypted=crypto.encrypt(req.api_key)))
    session.commit()
    # Las carreras creadas antes de vincular también pasan al calendario
    push_goals(linked_client(user, session),
               session.scalars(select(db.RaceGoal).where(db.RaceGoal.user_id == user.id)).all())
    return Response(status_code=204)


@router.delete("/me/intervals", status_code=204)
def unlink_intervals(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    session.execute(delete(db.IntervalsLink).where(db.IntervalsLink.user_id == user.id))
    session.commit()
    return Response(status_code=204)


@router.post("/me/plan/today/send", response_model=list[schemas.SentEvent])
def send_today(req: schemas.SendIn | None = None, user: db.User = Depends(auth.current_user),
               session: Session = Depends(db.get_session)):
    """Envía las sesiones de hoy al calendario de intervals.icu (resistencia estructurada; fuerza como lista).

    Con `sport`, solo la que vas a hacer: la planificada de ese deporte o una alternativa, que ocupa el sitio (mismo
    external_id) de la sesión a la que sustituye en el calendario.
    """
    client = linked_client(user, session)
    if client is None:
        raise HTTPException(409, "Vincula primero tu cuenta de intervals.icu (PUT /me/intervals)")
    ephys = physiology(client)
    today = date.today()
    plan = user_plan(user, session, today - timedelta(today.weekday()), ephys, client)
    sessions = next(d["sessions"] for d in plan["days"] if d["date"] == today.isoformat())
    if not sessions:
        raise HTTPException(409, "Hoy no hay sesiones que enviar")
    # (sesión, deporte de la planificada que ocupa en el calendario)
    chosen = [(s, s["sport"]) for s in sessions]
    if req and req.sport:
        options = [(s, s["sport"]) for s in sessions] + [(a, s["sport"]) for s in sessions
                                                         for a in s.get("alternatives", [])]
        chosen = [next((o for o in options if o[0]["sport"] == req.sport), None)]
        if chosen[0] is None:
            raise HTTPException(404, "Hoy no hay una sesión de ese deporte")
    events = [workout_text.strength_event(s, today.isoformat()) if s["sport"] == "strength"
              else workout_text.event(s, today.isoformat(), ephys, key=slot) for s, slot in chosen]
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
    goals = session.scalars(select(db.RaceGoal).where(db.RaceGoal.user_id == user.id)).all()
    verified = session.get(db.EmailVerification, user.id)
    return {"account": {"email": user.email, "birth_year": user.birth_year, "created_at": user.created_at.isoformat(),
                        "health_consent_at": user.health_consent_at.isoformat(), "terms_version": user.terms_version,
                        "email_verified_at": verified.verified_at.isoformat() if verified else None},
            "profile": {"plan": row.plan, "screening": row.screening, "block_start": row.block_start.isoformat()} if row else None,
            "intervals": {"athlete_id": link.athlete_id, "linked_at": link.linked_at.isoformat()} if link else None,
            "goals": [{"name": g.name, "sport": g.sport, "date": g.date.isoformat(), "priority": g.priority,
                       "distance_km": g.distance_km, "trail": g.trail} for g in goals],
            "sessions": [{"date": x.date.isoformat(), "sport": x.sport, "rpe": x.rpe} for x in logs],
            "readiness": [{"date": x.date.isoformat(), "sleep": x.sleep, "energy": x.energy, "soreness": x.soreness}
                          for x in ready]}


@router.delete("/me", status_code=204)
def delete_account(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)):
    """Borrado inmediato de la cuenta y de todos sus datos (RGPD art. 17, CUMPLIMIENTO #20)."""
    for model in (db.SessionLog, db.ReadinessLog, db.HealthProfileRow, db.IntervalsLink, db.WeekChange, db.RaceGoal,
                  db.EmailVerification):
        session.execute(delete(model).where(model.user_id == user.id))
    session.delete(session.get(db.User, user.id))
    session.commit()
    return Response(status_code=204)
