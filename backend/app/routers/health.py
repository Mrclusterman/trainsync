"""Planes de salud sin fecha: ponerse en forma, fuerza y músculo, perder grasa (docs/proyecto/DISENO.md).

shortcut: el perfil llega en cada petición porque aún no hay usuarios ni base de datos (Fase 1); guardarlo al tenerla.
"""
from datetime import date, timedelta

from fastapi import APIRouter

from app import schemas
from app.services import health_plan as hp
from app.services import screening

router = APIRouter()


@router.post("/health-plan", response_model=schemas.HealthPlan)
def health_plan(req: schemas.HealthPlanRequest):
    check = screening.screen(screening.Screening(**req.screening.model_dump()))
    p = hp.HealthProfile(goal=req.goal, days=dict(req.days), strength=req.strength, cardio=list(req.cardio),
                         level=req.level, age=req.age, intensity_cap=check.intensity_cap)
    today = date.today()
    start = req.start or today - timedelta(today.weekday())
    if check.clearance == "stop":  # síntomas estando activo: el algoritmo del ACSM manda parar y consultar
        return {"goal": req.goal, "week_in_block": req.week_in_block, "dose": {"aerobic": 0, "strength_days": 0, "sets": 0},
                "equivalent_min": 0, "warnings": [check.message], "screening": check.__dict__,
                "days": [{"date": (start + timedelta(d)).isoformat(), "weekday": hp.WEEKDAYS[d], "sessions": []}
                         for d in range(7)]}
    plan = hp.week_plan(p, start, req.adherence, req.week_in_block) | {"screening": check.__dict__}
    if check.clearance != "none":
        plan["warnings"].insert(0, check.message)
    if req.readiness:
        status = hp.readiness(req.readiness.sleep, req.readiness.energy, req.readiness.soreness)
        for day in plan["days"]:
            if day["date"] == today.isoformat():
                day["sessions"], day["note"] = hp.adjust_day(p, day["sessions"], status, start.isocalendar().week)
    return plan
