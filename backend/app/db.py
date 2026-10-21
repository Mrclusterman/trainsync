"""Persistencia (Fase 1): usuarios, perfil del plan de salud, sesiones hechas y estado diario.

SQLite por defecto (archivo en data/); en producción, DATABASE_URL apunta a PostgreSQL. Datos mínimos (CUMPLIMIENTO #7):
año de nacimiento en vez de fecha, y nada que el motor no use.
"""
import os
import uuid
from datetime import date, datetime, timezone

from sqlalchemy import JSON, Date, DateTime, ForeignKey, Integer, String, UniqueConstraint, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///data/trainsync.db")
if DATABASE_URL.startswith("sqlite:///") and not DATABASE_URL.endswith(":memory:"):
    os.makedirs(os.path.dirname(DATABASE_URL.removeprefix("sqlite:///")) or ".", exist_ok=True)
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(engine, expire_on_commit=False)


def now() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email: Mapped[str] = mapped_column(String(254), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    birth_year: Mapped[int] = mapped_column(Integer)
    # Consentimiento explícito y separado para datos de salud (RGPD art. 9.2.a, CUMPLIMIENTO #6), con fecha y versión
    health_consent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    terms_version: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class HealthProfileRow(Base):
    __tablename__ = "health_profiles"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    plan: Mapped[dict] = mapped_column(JSON)  # objetivo, días, fuerza, resistencia, nivel
    screening: Mapped[dict] = mapped_column(JSON)  # respuestas del cribado del ACSM
    block_start: Mapped[date] = mapped_column(Date)  # lunes en que empezó el primer bloque


class SessionLog(Base):
    """Una sesión hecha: alimenta la adherencia de cada bloque (DISENO §4)."""
    __tablename__ = "session_logs"
    __table_args__ = (UniqueConstraint("user_id", "date", "sport"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    date: Mapped[date] = mapped_column(Date)
    sport: Mapped[str] = mapped_column(String(20))
    rpe: Mapped[int | None] = mapped_column(Integer, nullable=True)


class RaceGoal(Base):
    """Carrera u objetivo con fecha creado en la app (el modo carrera también lee los del calendario de intervals.icu)."""
    __tablename__ = "race_goals"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(80))
    sport: Mapped[str] = mapped_column(String(20))
    date: Mapped[date] = mapped_column(Date)
    priority: Mapped[str] = mapped_column(String(1))
    distance_km: Mapped[float | None] = mapped_column(nullable=True)
    trail: Mapped[bool] = mapped_column(default=False)


class WeekChange(Base):
    """Cambios de disponibilidad de una semana concreta (mover una sesión, "hoy no puedo"): minutos por día."""
    __tablename__ = "week_changes"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    week_start: Mapped[date] = mapped_column(Date, primary_key=True)
    changes: Mapped[dict] = mapped_column(JSON)
    since: Mapped[date] = mapped_column(Date)  # día del último cambio: los anteriores no se recalculan


class EmailVerification(Base):
    """Email confirmado. Tabla aparte para no alterar `users` (sin migraciones, ver init)."""
    __tablename__ = "email_verifications"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    verified_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class IntervalsLink(Base):
    """Cuenta de intervals.icu vinculada; la clave va cifrada (app/crypto.py)."""
    __tablename__ = "intervals_links"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    athlete_id: Mapped[str] = mapped_column(String(40))
    api_key_encrypted: Mapped[str] = mapped_column(String(500))
    linked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class ReadinessLog(Base):
    __tablename__ = "readiness_logs"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    date: Mapped[date] = mapped_column(Date, primary_key=True)
    sleep: Mapped[int] = mapped_column(Integer)
    energy: Mapped[int] = mapped_column(Integer)
    soreness: Mapped[int] = mapped_column(Integer)


def init() -> None:
    Base.metadata.create_all(engine)  # shortcut: sin migraciones; pasar a Alembic antes de la primera versión pública


def get_session():
    with SessionLocal() as session:
        yield session
