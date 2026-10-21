"""Contrato de la API (OpenAPI). El frontend genera sus tipos TypeScript a partir de estos modelos."""
from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

Zone = Literal["Z1", "Z2", "Z3", "Z4", "Z5", "Z6"]
Sport = Literal["running", "cycling", "swimming_pool", "swimming_open", "walking"]
SessionType = Literal["recovery", "aerobic", "long", "neuromuscular", "vo2max", "threshold", "tempo", "anaerobic",
                      "race_specific", "brick", "test"]
Phase = Literal["base_early", "base", "build", "peak", "taper"]


class Block(BaseModel):
    block: str
    reps: int
    minutes: float
    zone: Zone
    rpe: str
    rest_minutes: float | None = None
    rest_zone: Zone | None = None
    detail: str | None = None
    duty: float | None = None
    guide: str | None = None


class Session(BaseModel):
    id: str
    sport: Sport
    type: SessionType
    name: str
    goal: str
    duration_min: int
    tss: int
    blocks: list[Block]
    targets: dict[Zone, str]
    hr_cap: int | None = None
    if_bad: str
    if_good: str


class Strength(BaseModel):
    name: str
    duration_min: int
    dose: str
    rpe_planned: int
    tss: int
    exercises: list[str]
    order: str


class Day(BaseModel):
    date: date
    weekday: Literal["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
    phase: Phase
    recovery_week: bool
    reasons: list[str]
    main: Session | None
    alternatives: list[Session]
    strength: Strength | None


class Goal(BaseModel):
    name: str
    sport: str
    date: date
    priority: Literal["A", "B", "C"]
    distance_km: float | None = None
    trail: bool = False


class Plan(BaseModel):
    goal: Goal | None
    days: list[Day]


class Physiology(BaseModel):
    lthr: float | None
    lthr_by_sport: dict[str, float]
    max_hr: float | None
    rest_hr: float | None
    css: float | None


class SrpeWeek(BaseModel):
    load: float
    monotony: float | None
    strain: float


class LastActivity(BaseModel):
    name: str | None
    date: date
    sport: str
    duration_min: int
    tss: float | None
    hr_zone_secs: list[float]
    gray_zone: bool


class State(BaseModel):
    date: date
    ctl: float
    atl: float
    tsb: float
    hrv: float | None
    hrv_status: Literal["within_swc", "outside_swc"] | None
    sleep_score: float | None
    consecutive_days: int
    goal: Goal | None
    phase: Phase
    recovery_week: bool
    physiology: Physiology
    warnings: list[str]
    srpe_week: SrpeWeek
    needs_test: list[str]
    last_activity: LastActivity | None


class Sent(BaseModel):
    id: int
    date: date
    name: str
    description: str


# ---- Planes de salud sin fecha (docs/proyecto/DISENO.md)

Weekday = Literal["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]


class Readiness(BaseModel):
    sleep: int = Field(ge=1, le=5)
    energy: int = Field(ge=1, le=5)
    soreness: int = Field(ge=1, le=5)


class HealthPlanRequest(BaseModel):
    goal: Literal["fitness", "strength", "fat_loss"]
    days: dict[Weekday, int] = Field(description="Minutos disponibles por día")
    strength: Literal["gym", "bodyweight", "none"] = "gym"
    cardio: list[Literal["walking", "running", "cycling", "swimming_pool"]] = ["walking"]
    level: Literal["beginner", "intermediate", "advanced"] = "beginner"
    age: int | None = Field(None, ge=14, le=110)  # España: 14 años para consentir (CUMPLIMIENTO #17)
    parq_restricted: bool = False
    start: date | None = Field(None, description="Lunes de la semana; por defecto, el de esta semana")
    adherence: list[float] = Field([], description="Fracción de sesiones hechas en cada bloque terminado")
    week_in_block: int = Field(0, ge=0, le=3)
    readiness: Readiness | None = Field(None, description="Estado de hoy sin reloj (§8)")


class HealthCardio(Session):
    equivalent_min: int


class HealthStrength(BaseModel):
    sport: Literal["strength"]
    type: Literal["strength"]
    name: str
    patterns: list[str]
    sets_per_exercise: int
    exercises: list[str]
    rir: int
    duration_min: int
    tss: int
    notes: str


class HealthDay(BaseModel):
    date: date
    weekday: Weekday
    sessions: list[HealthStrength | HealthCardio]
    note: str | None = None


class HealthDose(BaseModel):
    aerobic: int
    strength_days: int
    sets: int


class HealthPlan(BaseModel):
    goal: str
    week_in_block: int
    dose: HealthDose
    equivalent_min: int
    days: list[HealthDay]
    warnings: list[str]
