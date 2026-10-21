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
(ver `docs/MOTOR_ENTRENAMIENTO.md` §11.2).

- Catálogo de 57 sesiones (`backend/app/services/session_catalog.py`) con rotación semanal: running por distancia
  (5K a ultra), trail, ciclismo, natación en piscina y aguas abiertas, triatlón/duatlón (brick), neuromusculares y tests.
- Biblioteca de fuerza por fase y TSS semanal según la rampa de CTL, repartido por tipo de sesión.
- RPE: objetivo por bloque, señal de fatiga (RPE alto con FC baja) y carga semanal sRPE con monotonía/strain.

## Arrancar

```bash
cp .env.example .env          # rellena INTERVALS_ATHLETE_ID e INTERVALS_API_KEY
# Con Docker (Windows/WSL): backend en :8000 y web en :3000
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
| `POST /plan/today/send` | Envía la sesión de hoy al calendario de intervals.icu como entrenamiento estructurado; intervals.icu la pasa al reloj si tienes activada la sincronización (Garmin, Wahoo, Zwift). Reenviar la actualiza (`external_id` por día) |

## Frontend web (`frontend-web/`)

Next.js 16 + TypeScript + Tailwind CSS 4 con el sistema de diseño "Athletic Precision": pantallas **Hoy** (estado de
forma, sesión del día con estructura, RPE y alternativas, próximos días, última actividad con zonas) y **Plan**
(14 días con bottom sheet). Los datos se piden desde Server Components, así que el navegador no habla con el backend.

```bash
cd frontend-web && npm install
npm run dev                 # http://localhost:3000 (backend en :8000)
npm run gen:types           # regenera src/lib/api-types.ts desde el OpenAPI del backend
```

`TRAINSYNC_API_URL` cambia la URL del backend (por defecto `http://localhost:8000`).

## Tests

```bash
cd backend && .venv/bin/pytest
```

## Licencia

Copyright (C) 2026 Gabriel Miralles Alvarez.

TrainSync es software libre bajo la [GNU Affero General Public License v3.0](LICENSE) (`AGPL-3.0-only`).
Puedes usarlo, estudiarlo, modificarlo y redistribuirlo; si ofreces una versión modificada como servicio
en red, debes publicar su código fuente bajo la misma licencia. Componentes de terceros: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

