"""Cliente de la API de intervals.icu (memoria técnica, sección 7). Solo escribe entrenamientos planificados."""
from datetime import date

import httpx

BASE_URL = "https://intervals.icu/api/v1"


class IntervalsClient:
    def __init__(self, athlete_id: str, api_key: str, transport: httpx.BaseTransport | None = None):
        # Basic Auth: usuario literal "API_KEY", contraseña = clave del atleta
        self.http = httpx.Client(base_url=f"{BASE_URL}/athlete/{athlete_id}", auth=("API_KEY", api_key),
                                 timeout=20, transport=transport)

    def _get(self, path: str, **params) -> list | dict:
        r = self.http.get(path, params={k: str(v) for k, v in params.items()})
        r.raise_for_status()
        return r.json()

    def athlete(self) -> dict:
        return self._get("")

    def wellness(self, oldest: date, newest: date) -> list[dict]:
        return self._get("/wellness", oldest=oldest, newest=newest)

    def activities(self, oldest: date, newest: date) -> list[dict]:
        return self._get("/activities", oldest=oldest, newest=newest)

    def races(self, oldest: date, newest: date) -> list[dict]:
        """Objetivos = eventos de carrera A/B/C del calendario de intervals.icu."""
        return self._get("/events", oldest=oldest, newest=newest, category="RACE_A,RACE_B,RACE_C")

    def streams(self, activity_id: str, types: str = "heartrate,velocity_smooth,grade_smooth") -> dict[str, list]:
        """Datos segundo a segundo de una actividad (endpoint fuera de /athlete/{id})."""
        r = self.http.get(f"{BASE_URL}/activity/{activity_id}/streams", params={"types": types})
        r.raise_for_status()
        return {s["type"]: s.get("data") or [] for s in r.json()}

    def upsert_workouts(self, events: list[dict]) -> list[dict]:
        """Crea o actualiza (por external_id) entrenamientos del calendario; intervals.icu los pasa al reloj si está configurado."""
        r = self.http.post("/events/bulk", params={"upsert": "true", "upsertOnUid": "false", "updatePlanApplied": "false"}, json=events)
        r.raise_for_status()
        return r.json()
