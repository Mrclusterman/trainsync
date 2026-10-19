"""Configuración por variables de entorno (ver .env.example)."""
import os
from datetime import date

from app.services.training_engine import Profile


def _list(name: str, default: str) -> list[str]:
    return [x.strip() for x in os.getenv(name, default).split(",") if x.strip()]


def _num(name: str) -> float | None:
    return float(os.environ[name]) if os.getenv(name) else None


INTERVALS_ATHLETE_ID = os.getenv("INTERVALS_ATHLETE_ID", "")
INTERVALS_API_KEY = os.getenv("INTERVALS_API_KEY", "")


def load_profile() -> Profile:
    """Perfil base desde el entorno; los fisiológicos vacíos se rellenan desde intervals.icu."""
    start = os.getenv("PLAN_START")
    return Profile(
        active_sports=_list("ACTIVE_SPORTS", "running,cycling"),
        strength=os.getenv("STRENGTH", "gym"),
        rest_days=_list("REST_DAYS", "MON"),
        lthr=_num("LTHR"), max_hr=_num("MAX_HR"), rest_hr=_num("REST_HR"), css=_num("CSS_SEC_PER_100M"),
        plan_start=date.fromisoformat(start) if start else None,
    )
