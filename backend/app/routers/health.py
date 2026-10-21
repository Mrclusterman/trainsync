"""Planes de salud sin fecha: ponerse en forma, fuerza y músculo, perder grasa (docs/proyecto/DISENO.md).

shortcut: el perfil llega en cada petición porque aún no hay usuarios ni base de datos (Fase 1); guardarlo al tenerla.
"""
from datetime import date, timedelta

from fastapi import APIRouter

from app import schemas
from app.services import health_plan as hp

router = APIRouter()


@router.post("/health-plan", response_model=schemas.HealthPlan)
def health_plan(req: schemas.HealthPlanRequest):
    p = hp.HealthProfile(goal=req.goal, days=dict(req.days), strength=req.strength, cardio=list(req.cardio),
                         level=req.level, age=req.age, parq_restricted=req.parq_restricted)
    today = date.today()
    start = req.start or today - timedelta(today.weekday())
    plan = hp.week_plan(p, start, req.adherence, req.week_in_block)
    if req.readiness:
        status = hp.readiness(req.readiness.sleep, req.readiness.energy, req.readiness.soreness)
        for day in plan["days"]:
            if day["date"] == today.isoformat():
                day["sessions"], day["note"] = hp.adjust_day(p, day["sessions"], status, start.isocalendar().week)
    return plan
