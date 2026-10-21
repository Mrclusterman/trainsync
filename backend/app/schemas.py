"""Contrato de la API (OpenAPI). El frontend genera sus tipos TypeScript a partir de estos modelos."""
from datetime import date
from typing import Literal

from pydantic import BaseModel

Zone = Literal["Z1", "Z2", "Z3", "Z4", "Z5", "Z6"]
Sport = Literal["running", "cycling", "swimming_pool", "swimming_open"]
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
