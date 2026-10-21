"""Planes de salud sin fecha: ponerse en forma, fuerza y músculo, perder grasa (docs/proyecto/DISENO.md).

POST /health-plan recibe el perfil en la petición (sin cuenta); con cuenta, /me/plan lo lee de la base de datos.
"""
from datetime import date, timedelta

from fastapi import APIRouter

from app import schemas
from app.services import health_plan as hp
from app.services import screening


def profile_from(req: schemas.HealthPlanRequest) -> tuple[hp.HealthProfile, screening.Result]:
    check = screening.screen(screening.Screening(**req.screening.model_dump()))
    p = hp.HealthProfile(goal=req.goal, days=dict(req.days), strength=req.strength, cardio=list(req.cardio),
                         level=req.level, age=req.age, intensity_cap=check.intensity_cap)
    return p, check


def build_plan(req: schemas.HealthPlanRequest, monday: date, adherence: list[float], week_in_block: int,
               readiness: schemas.Readiness | None) -> dict:
    p, check = profile_from(req)
    if check.clearance == "stop":  # síntomas estando activo: el algoritmo del ACSM manda parar y consultar
        return {"goal": req.goal, "week_in_block": week_in_block, "dose": {"aerobic": 0, "strength_days": 0, "sets": 0},
                "equivalent_min": 0, "warnings": [check.message], "screening": check.__dict__,
                "days": [{"date": (monday + timedelta(d)).isoformat(), "weekday": hp.WEEKDAYS[d], "sessions": []}
                         for d in range(7)]}
    plan = hp.week_plan(p, monday, adherence, week_in_block) | {"screening": check.__dict__}
    if check.clearance != "none":
        plan["warnings"].insert(0, check.message)
    if readiness:
        status = hp.readiness(readiness.sleep, readiness.energy, readiness.soreness)
        for day in plan["days"]:
            if day["date"] == date.today().isoformat():
                day["sessions"], day["note"] = hp.adjust_day(p, day["sessions"], status, monday.isocalendar().week)
    return plan


router = APIRouter()


@router.post("/health-plan", response_model=schemas.HealthPlan)
def health_plan(req: schemas.HealthPlanRequest):
    today = date.today()
    monday = req.start or today - timedelta(today.weekday())
    return build_plan(req, monday, req.adherence, req.week_in_block, req.readiness)
