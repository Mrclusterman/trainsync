# TrainSync

Planes de entrenamiento de resistencia gratuitos y open source sobre los datos de intervals.icu.
Especificación: [`docs/MEMORIA_TECNICA.md`](docs/MEMORIA_TECNICA.md) · Motor: [`docs/MOTOR_ENTRENAMIENTO.md`](docs/MOTOR_ENTRENAMIENTO.md) · Diseño: [`docs/DISENO_ATHLETIC_PRECISION.md`](docs/DISENO_ATHLETIC_PRECISION.md)

## Estado (v0.1)

Backend FastAPI monousuario con el motor de entrenamiento (sección 8 de la memoria):

- Carga: hrTSS, TSS por potencia, sTSS (CSS), rTSS de fuerza, CTL/ATL/TSB.
- Estado HRV (media 7 d de ln RMSSD vs SWC), ajuste diario por TSB (< −30), HRV, sueño y días seguidos.
- Zonas de FC de Friel por deporte y techo `LTHR − 30` en sesiones fáciles.
- Periodización base temprana (VO2max) → base → construcción → peak → taper, con semanas 3+1.
- Plantilla polarizada 80/20; Z3 nunca es objetivo; detección de zona gris en la última actividad.
- Propuesta diaria: sesión principal + alternativa de igual TSS por deporte activo + fuerza.
- Objetivo = carrera A/B/C más próxima del calendario de intervals.icu.

Lo marcado `[Inferencia]` en `backend/app/services/training_engine.py` no tiene evidencia publicada
(ver `docs/MOTOR_ENTRENAMIENTO.md` §11.2). El catálogo completo de sesiones del documento aún no está en el código.

## Arrancar

```bash
cp .env.example .env          # rellena INTERVALS_ATHLETE_ID e INTERVALS_API_KEY
# Con Docker (Windows/WSL):
docker compose up --build
# Sin Docker:
cd backend && python3 -m venv .venv && .venv/bin/pip install -e '.[dev]'
set -a && . ../.env && set +a && .venv/bin/uvicorn app.main:app --reload
```

Swagger UI: http://localhost:8000/docs

| Endpoint | Devuelve |
|---|---|
| `GET /state` | CTL, ATL, TSB, HRV, sueño, fase, objetivo y análisis de zona gris de la última actividad |
| `GET /plan/today` | Propuesta de hoy ajustada a tu estado |
| `GET /plan?days=14` | Plan de N días (solo hoy usa el estado real; se recalcula a diario) |

## Tests

```bash
cd backend && .venv/bin/pytest
```
