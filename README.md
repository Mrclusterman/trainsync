# TrainSync

Planes de entrenamiento de resistencia gratuitos y open source sobre los datos de intervals.icu.

**Compromiso:** TrainSync es y será gratis, **sin niveles de pago, sin anuncios y sin notificaciones comerciales**.
Ninguna función se bloquea para empujar a pagar. Se revisa en cada versión ([`docs/CUMPLIMIENTO.md`](docs/CUMPLIMIENTO.md), punto 9).
Especificación: [`docs/MEMORIA_TECNICA.md`](docs/MEMORIA_TECNICA.md) · Motor: [`docs/MOTOR_ENTRENAMIENTO.md`](docs/MOTOR_ENTRENAMIENTO.md) · Diseño: [`docs/DISENO_ATHLETIC_PRECISION.md`](docs/DISENO_ATHLETIC_PRECISION.md)

## Hacia dónde va

TrainSync pasa a ser **apps nativas que funcionan sin servidor**: el motor corre en el propio móvil y tus datos de salud
no salen de él ([`docs/VISION.md`](docs/VISION.md)).

| Carpeta | Qué es |
|---|---|
| `shared/` | Núcleo compartido en Kotlin Multiplatform: el motor (la única fuente desde el 2026-10-29), la conexión con intervals.icu desde el móvil y el estado de cada pantalla (`app/`) |
| `androidApp/` | App de Android (Jetpack Compose): inicio con objetivo, Hoy, Calendario, Progreso y Perfil |
| `iosApp/` | App de iOS (SwiftUI), las mismas pantallas. El proyecto de Xcode se genera con XcodeGen (`iosApp/project.yml`) |
| `prototype/` | El prototipo web anterior (FastAPI + Next.js). Su motor en Python está congelado: los cambios del motor se hacen solo en `shared/` |

```bash
./gradlew :shared:jvmTest                 # tests del núcleo y validación del motor (docs/proyecto/VALIDACION.md)
./gradlew :androidApp:assembleDebug       # APK de depuración en androidApp/build/outputs/apk/debug/
```

La APK de cada push queda como artefacto `trainsync-debug-apk` en GitHub Actions, y las capturas de iOS como `ios-screenshots`.

iOS solo compila en macOS: la CI de GitHub (`.github/workflows/build.yml`) genera el proyecto con XcodeGen, lo compila
para el simulador y pasa los tests del núcleo en iOS en cada push. Necesita JDK 21 y el SDK de Android (compileSdk 37).

## El prototipo (v0.1)

Backend FastAPI monousuario con el motor de entrenamiento (sección 8 de la memoria):

- Carga: hrTSS, TSS por potencia, sTSS (CSS), rTSS de fuerza, CTL/ATL/TSB.
- Estado HRV (media 7 d de ln RMSSD vs SWC), ajuste diario por TSB (< −30), HRV, sueño y días seguidos.
- Zonas de FC de Friel por deporte y techo `LTHR − 30` en sesiones fáciles.
- Periodización base temprana (VO2max) → base → construcción → peak → taper, con semanas 3+1.
- Plantilla polarizada 80/20; Z3 nunca es objetivo; detección de zona gris en la última actividad.
- Propuesta diaria: sesión principal + alternativa de igual TSS por deporte activo + fuerza.
- Objetivo = carrera A/B/C más próxima, creada en la app o en el calendario de intervals.icu.
- Como las sugerencias de Garmin, pero con la fórmula a la vista ([`MOTOR_ENTRENAMIENTO.md`](docs/MOTOR_ENTRENAMIENTO.md) §12–§15):
  la calidad del día cubre el tipo de carga que te falta (aeróbico bajo, alto o anaeróbico, 28 días), sesiones tempo y
  anaeróbicas, vuelta a la base tras 7 días sin entrenar y ritmos de carrera calculados desde tu FC con tus propias carreras.

Lo marcado `[Inferencia]` en `shared/src/commonMain/kotlin/io/github/mrclusterman/trainsync/engine/` no tiene evidencia publicada
(ver `docs/MOTOR_ENTRENAMIENTO.md` §11.2).

- Catálogo de 57 sesiones (`shared/.../engine/Catalog.kt`) con rotación semanal: running por distancia
  (5K a ultra), trail, ciclismo, natación en piscina y aguas abiertas, triatlón/duatlón (brick), neuromusculares y tests.
- Biblioteca de fuerza por fase y TSS semanal según la rampa de CTL, repartido por tipo de sesión.
- RPE: objetivo por bloque, señal de fatiga (RPE alto con FC baja) y carga semanal sRPE con monotonía/strain.

## Arrancar

```bash
cd prototype && cp .env.example .env   # rellena JWT_SECRET y ENCRYPTION_KEY; cada usuario vincula su intervals.icu en la web
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
| `POST /health-plan` | Plan semanal sin fecha para ponerse en forma o ganar fuerza y músculo: gimnasio o peso corporal, solo o con caminar, correr, bici o natación ([`docs/proyecto/DISENO.md`](docs/proyecto/DISENO.md)). El perfil va en la petición hasta que haya usuarios (Fase 1) |
| `POST /auth/register`, `POST /auth/login` | Cuenta con consentimiento explícito y separado para datos de salud y edad mínima de 14 años |
| `POST /auth/forgot`, `POST /auth/reset`, `POST /auth/verify`, `GET /me`, `POST /me/verify` | Recuperar la contraseña (enlace de 1 h, de un solo uso, cierra las demás sesiones; la respuesta no revela si la cuenta existe) y confirmar el email. Login limitado a 10 intentos por cuenta cada 15 min. Emails por SMTP (`.env.example`); sin SMTP se escriben en el log, solo para desarrollo |
| `PUT /me/profile`, `GET /me/plan` | Perfil guardado (objetivo, días, actividades, cribado del ACSM) y plan de la semana; la adherencia de cada bloque sale de las sesiones registradas |
| `GET /me/goals`, `POST /me/goals`, `PUT /me/goals/{id}`, `DELETE /me/goals/{id}` | Carreras objetivo creadas en la app (nombre, deporte, fecha, distancia, prioridad A/B/C, trail). Se copian al calendario de intervals.icu (RACE_A/B/C, `external_id` trainsync-goal-…) y se borran con él. El plan de carrera elige entre estas y las del calendario: prioridad A > B > C y, a igual prioridad, la más cercana |
| `POST /me/week/move`, `POST /me/week/day`, `DELETE /me/week` | Mover una sesión a otro día, "no puedo este día" o "puedo entrenar": la semana se recalcula desde hoy; los días pasados no cambian |
| `POST /me/sessions`, `POST /me/readiness` | Marcar una sesión como hecha; estado del día sin reloj |
| `PUT /me/intervals`, `POST /me/plan/today/send` | Vincular intervals.icu una vez (clave cifrada con AES-256-GCM) y enviar al reloj las sesiones de hoy. Con `{"sport": …}`, solo la que vas a hacer: la planificada o una alternativa en otro de tus deportes (mismo trabajo aeróbico), que ocupa su sitio en el calendario |
| `GET /me/export`, `DELETE /me` | Exportar todos tus datos (RGPD art. 20) y borrar la cuenta (art. 17) |
| `GET /state`, `GET /plan`, `GET /plan/today` | Plan de carrera con la sesión del usuario y **su** cuenta de intervals.icu vinculada (401 sin sesión; 409 sin vincular). Nunca se usa una cuenta del servidor |
| `POST /plan/today/send` | Envía la sesión de hoy (o, con `{"sport": …}`, la alternativa que vas a hacer) al calendario de intervals.icu como entrenamiento estructurado; intervals.icu la pasa al reloj si tienes activada la sincronización (Garmin, Wahoo, Zwift). Reenviar la actualiza (`external_id` por día) |

## Frontend web (`prototype/web/`)

Next.js 16 + TypeScript + Tailwind CSS 4 con el sistema de diseño "Athletic Precision": pantallas **Hoy** (estado de
forma, sesión del día con estructura, RPE y alternativas, próximos días, última actividad con zonas) y **Plan**
(14 días con bottom sheet). Los datos se piden desde Server Components, así que el navegador no habla con el backend.

```bash
cd prototype/web && npm install
npm run dev                 # http://localhost:3000 (backend en :8000)
npm run gen:types           # regenera src/lib/api-types.ts desde el OpenAPI del backend
```

`TRAINSYNC_API_URL` cambia la URL del backend (por defecto `http://localhost:8000`).

## Tests

```bash
cd prototype/backend && .venv/bin/pytest
```

## Licencia

Copyright (C) 2026 Gabriel Miralles Alvarez.

TrainSync es software libre bajo la [GNU Affero General Public License v3.0](LICENSE) (`AGPL-3.0-only`).
Puedes usarlo, estudiarlo, modificarlo y redistribuirlo; si ofreces una versión modificada como servicio
en red, debes publicar su código fuente bajo la misma licencia. Componentes de terceros: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

