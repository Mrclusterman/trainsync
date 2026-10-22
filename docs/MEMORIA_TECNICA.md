# MEMORIA TÉCNICA — TrainSync
### App de planificación de entrenamiento basada en intervals.icu

> **Versión:** 0.5 — Reglas de TSB, HRV, zonas de running y clasificación de Z4 corregidas según bibliografía (ver `MOTOR_ENTRENAMIENTO.md` §11.1)  
> **Fecha última actualización:** 2026-10-19  
> **Estado:** Pre-desarrollo — Aprobado para inicio

---

## ÍNDICE

1. [Descripción del Proyecto](#1-descripción-del-proyecto)
2. [Requisitos Funcionales](#2-requisitos-funcionales)
3. [Requisitos No Funcionales](#3-requisitos-no-funcionales)
4. [Arquitectura del Sistema](#4-arquitectura-del-sistema)
5. [Stack Tecnológico Justificado](#5-stack-tecnológico-justificado)
6. [Modelo de Datos](#6-modelo-de-datos)
7. [Integración con intervals.icu](#7-integración-con-intervalsicu)
8. [Motor de Entrenamiento](#8-motor-de-entrenamiento)
9. [Roadmap por Fases](#9-roadmap-por-fases)
10. [Estructura de Directorios del Proyecto](#10-estructura-de-directorios-del-proyecto)
11. [Decisiones de Diseño Registradas](#11-decisiones-de-diseño-registradas)
12. [Glosario Técnico](#12-glosario-técnico)
13. [Control de Cambios del Documento](#13-control-de-cambios-del-documento)

---

## 1. Descripción del Proyecto

### 1.1 Problema que resuelve

Las apps de planificación de entrenamiento de calidad (TrainingPeaks, Today's Plan, Intervals.icu Premium) son de pago. El objetivo de TrainSync es ofrecer planes de entrenamiento dinámicos, científicamente fundamentados y completamente gratuitos, usando los datos que el usuario ya genera automáticamente con su hardware (Garmin, iGPSport) a través de intervals.icu.

### 1.2 Propuesta de valor

- **Gratuito y open source**: cualquier persona puede usarlo o auto-hostearlo
- **Basado en datos reales**: no usa valores predeterminados, usa los datos reales del usuario
- **Dinámico**: el plan se recalcula cada día según sueño, HRV, fatiga acumulada y actividad reciente
- **Multi-deporte completo**: running, ciclismo (con o sin potenciómetro), natación (piscina y aguas abiertas) y triatlón en todas sus distancias
- **Orientado a objetivos**: prepara al usuario para eventos concretos (gran fondo, maratón, medio ironman, sprint triatlón, etc.)
- **Plan diario flexible**: propuesta principal según el objetivo prioritario del día + alternativas en los otros deportes activos del usuario
- **Onboarding inteligente**: pregunta qué deportes practica el usuario, y si faltan datos fisiológicos, propone tests de campo para obtenerlos

### 1.3 Usuarios objetivo

Cualquier persona que use intervals.icu para registrar sus actividades de running, ciclismo o natación (o una combinación de ellas) y quiera planificación de entrenamiento estructurada sin coste. Incluye triatletas de cualquier distancia.

---

## 2. Requisitos Funcionales

### RF-01: Autenticación y gestión de usuarios
- El usuario puede registrarse con email y contraseña
- El usuario puede conectar su cuenta de intervals.icu mediante API key
- El sistema almacena de forma cifrada las credenciales de intervals.icu
- El usuario puede desconectar su cuenta de intervals.icu en cualquier momento
- El usuario puede eliminar su cuenta y todos sus datos

### RF-02: Sincronización de datos desde intervals.icu
- El sistema sincroniza automáticamente actividades, wellness y datos de sueño
- La sincronización se ejecuta en background (no bloquea la interfaz)
- El usuario puede forzar una sincronización manual
- El sistema registra la fecha/hora de la última sincronización exitosa
- El sistema notifica al usuario si la sincronización falla

### RF-03: Onboarding y configuración inicial
- **Paso 1 — Selección de deportes**: el primer paso del onboarding es preguntar qué deportes quiere practicar el usuario. Opciones: Running, Ciclismo, Natación (piscina), Natación (aguas abiertas). El sistema solo mostrará opciones y recomendará entrenamientos de los deportes seleccionados. Esta selección es editable en cualquier momento desde el perfil.
- **Paso 2 — Conexión intervals.icu**: introducir API key para importar historial y datos fisiológicos
- **Paso 3 — Datos fisiológicos**: el sistema detecta qué datos están disponibles en intervals.icu para los deportes seleccionados
- Si faltan datos críticos (FC máxima, LTHR, CSS para natación), el sistema propone tests de campo específicos por deporte
- Los tests propuestos son sesiones de entrenamiento reales y descargables
- El usuario puede introducir manualmente datos fisiológicos si los conoce
- El sistema revisa periódicamente si los datos fisiológicos están desactualizados (> 3 meses) y sugiere actualización

### RF-04: Gestión de objetivos
- El usuario puede crear múltiples objetivos simultáneos de distintos deportes
- **Deportes por objetivo**: Running, Ciclismo, Natación, Triatlón (combina los tres) o Duatlón (combina running y ciclismo)
- **Tipos de objetivo soportados:**
  - Evento por distancia (ej: maratón, gran fondo 100km, 10km natación)
  - Evento por tiempo objetivo (ej: bajar de 4h en maratón)
  - Triatlón por formato: Sprint, Olímpico, 70.3 (Medio Ironman), Ironman completo, o distancia personalizada
  - Duatlón por formato: Sprint, Estándar, Long, o distancia personalizada
  - Mejora de rendimiento (ej: mejorar ritmo en 5K, subir FTP, bajar tiempo en 1500m natación)
- **Distancias de referencia de triatlón:**

| Formato | Natación | Ciclismo | Running |
|---|---|---|---|
| Sprint | 750 m | 20 km | 5 km |
| Olímpico | 1.500 m | 40 km | 10 km |
| 70.3 / Medio Ironman | 1.900 m | 90 km | 21,1 km |
| Ironman | 3.800 m | 180 km | 42,2 km |
| Personalizado | variable | variable | variable |

- **Distancias de referencia de duatlón:**

| Formato | Running 1 | Ciclismo | Running 2 |
|---|---|---|---|
| Sprint | 5 km | 20 km | 2,5 km |
| Estándar | 10 km | 40 km | 5 km |
| Long | 10 km | 60 km | 10 km |
| Personalizado | variable | variable | variable |

- Cada objetivo incluye: tipo, deporte(s), fecha objetivo, distancia/tiempo objetivo, nivel de importancia (A/B/C)
- El sistema calcula automáticamente las semanas disponibles hasta el objetivo
- El usuario puede editar o eliminar objetivos

### RF-05: Generación del plan de entrenamiento
- El sistema genera un plan diario para los próximos N días (configurable, por defecto 14)
- **Estructura de la propuesta diaria:**
  - **Sesión principal**: determinada por el objetivo prioritario activo, el deporte que toca según la periodización y el estado del atleta (HRV, sueño, TSB). Incluye tipo de sesión, deporte, duración, zonas objetivo, descripción y objetivo fisiológico del día.
  - **Alternativas**: una sesión equivalente en carga para cada uno de los otros deportes activos del usuario (solo los que el usuario ha indicado que practica). Si el usuario solo hace running, no habrá alternativas de natación ni ciclismo.
  - El usuario puede seleccionar cualquiera de las opciones. La elección queda registrada para el recálculo del día siguiente.
- El plan se recalcula automáticamente cada día al consultar la app
- El recálculo considera: fatiga acumulada (ATL), forma actual (TSB), HRV, calidad del sueño, y semanas hasta el objetivo

### RF-06: Visualización y seguimiento
- Dashboard con métricas de forma: CTL (fitness), ATL (fatiga), TSB (forma)
- Calendario de entrenamiento con actividades realizadas y sesiones planificadas
- Comparativa entre lo planificado y lo ejecutado
- Progreso hacia el objetivo con proyección

### RF-08: Entrenamiento de fuerza
- El sistema incluye el entrenamiento de fuerza como parte del plan semanal, no como actividad opcional
- El usuario puede indicar si tiene acceso a gimnasio (pesas, máquinas) o solo entrena con el propio peso corporal
- Las sesiones de fuerza propuestas se adaptan al deporte objetivo (fuerza de tren inferior para running/ciclismo, fuerza de tren superior y core para natación/triatlón)
- La carga de una sesión de fuerza se cuantifica mediante **RPE × duración** (ver sección 8.1), ya que intervals.icu no proporciona métricas equivalentes al TSS para fuerza
- El usuario puede registrar manualmente el RPE percibido tras una sesión de fuerza para que el sistema actualice la carga del día
- Las sesiones de fuerza se programan estratégicamente: mayor peso en fases Base y Construcción, reducción en Peak y eliminación en Taper

### RF-09: Configuración de perfil de entrenamiento
- **Deportes activos** (selección múltiple): Running, Ciclismo, Natación en piscina, Natación en aguas abiertas. Solo se generarán sesiones y alternativas de los deportes marcados.
- **Entrenamiento de fuerza**: el usuario indica si quiere incluirlo y si tiene acceso a gimnasio o solo peso corporal
- Días disponibles para entrenar por semana (selección por día)
- Horas disponibles aproximadas por sesión
- Disponibilidad de potenciómetro en bici (condiciona métricas de carga en ciclismo)
- Acceso a piscina (días por semana, longitud del carril: 25m o 50m)
- Nivel de experiencia por deporte (principiante / intermedio / avanzado) — puede variar entre deportes
- Toda esta configuración es editable en cualquier momento

---

## 3. Requisitos No Funcionales

### RNF-01: Rendimiento
- La consulta del plan diario debe responder en < 2 segundos
- La sincronización de datos no debe bloquear la interfaz de usuario
- La app móvil debe ser funcional con conexión 3G

### RNF-02: Portabilidad y despliegue
- Todo el sistema debe ejecutarse con `docker compose up`
- El proyecto debe ser completamente reproducible en cualquier SO con Docker instalado
- Los datos deben persistir entre reinicios del contenedor (volúmenes Docker)

### RNF-03: Seguridad
- Las API keys de intervals.icu se almacenan cifradas en base de datos (AES-256)
- Las contraseñas se almacenan con hashing bcrypt
- Toda comunicación usa HTTPS (TLS) en producción
- Los tokens JWT tienen expiración configurable

### RNF-04: Gratuidad
- El sistema no depende de ningún servicio externo de pago para su funcionamiento básico
- El LLM (si se usa) debe ser auto-hospedado mediante Ollama o equivalente
- Si un usuario quiere usar una API LLM externa, puede configurarla opcionalmente con su propia clave

### RNF-05: Escalabilidad mínima
- El diseño debe soportar múltiples usuarios concurrentes desde el inicio
- La base de datos debe usar un esquema multi-tenant correcto (aislamiento por usuario)

---

## 4. Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTE                                   │
│  ┌──────────────────────┐    ┌──────────────────────────────┐   │
│  │   Web (Next.js)      │    │   Mobile (React Native/Expo) │   │
│  │   Puerto 3000        │    │   Build APK/IPA              │   │
│  └──────────┬───────────┘    └──────────────┬───────────────┘   │
└─────────────┼──────────────────────────────┼────────────────────┘
              │ HTTPS/REST + WebSocket        │
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BACKEND (FastAPI)                             │
│  Puerto 8000                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Auth API    │  │  Training    │  │  Intervals.icu       │  │
│  │  /auth/*     │  │  Engine API  │  │  Sync Service        │  │
│  │              │  │  /plan/*     │  │  (Background Worker) │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  User API    │  │  Goals API   │  │  Wellness API        │  │
│  │  /users/*    │  │  /goals/*    │  │  /wellness/*         │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                  ▼
┌─────────────────┐  ┌──────────────────┐  ┌──────────────┐
│  PostgreSQL     │  │     Redis         │  │   Ollama     │
│  + TimescaleDB  │  │  Cache + Queue    │  │ (LLM local)  │
│  Puerto 5432    │  │  Puerto 6379      │  │ Puerto 11434 │
└─────────────────┘  └──────────────────┘  └──────────────┘
                                │
                                ▼
                     ┌──────────────────┐
                     │  Celery Worker   │
                     │  (Sync jobs,     │
                     │  plan recalc)    │
                     └──────────────────┘
                                │
                                ▼ HTTP
                     ┌──────────────────────┐
                     │   intervals.icu API  │
                     │   (servicio externo) │
                     └──────────────────────┘
```

### 4.1 Descripción de componentes

| Componente | Responsabilidad |
|---|---|
| **Next.js (Web)** | Frontend web. SSR para SEO, SPA para la app autenticada |
| **React Native + Expo** | App móvil iOS/Android. Misma API que el web |
| **FastAPI (Backend)** | API REST. Lógica de negocio. Orquesta todos los servicios |
| **PostgreSQL + TimescaleDB** | Base de datos principal. TimescaleDB gestiona series temporales de actividades y wellness |
| **Redis** | Cache de sesiones, cache de planes calculados, broker de mensajes para Celery |
| **Celery Worker** | Tareas asíncronas: sincronización periódica con intervals.icu, recálculo de planes |
| **Ollama** | LLM local opcional para generación de descripciones de entrenamiento en lenguaje natural |
| **intervals.icu API** | Fuente de datos externa. Solo lectura desde TrainSync |

---

## 5. Stack Tecnológico Justificado

### 5.1 Backend — Python 3.12 + FastAPI

**Justificación:**
- Python es el lenguaje estándar en ciencia de datos y machine learning. El motor de entrenamiento (TSS, CTL, ATL) se implementa de forma natural con NumPy/Pandas.
- FastAPI genera documentación OpenAPI automática (Swagger UI), lo que facilita el desarrollo paralelo del frontend.
- Alto rendimiento asíncrono (async/await nativo) sin la complejidad de Node.js.
- Ecosistema científico maduro: NumPy, Pandas, SQLAlchemy, Pydantic.

### 5.2 Base de datos — PostgreSQL 16 + extensión TimescaleDB

**Justificación:**
- Los datos de entrenamiento (actividades, wellness, frecuencia cardíaca diaria) son series temporales. TimescaleDB añade hypertables optimizadas para consultas temporales sobre PostgreSQL estándar.
- Soporta queries complejas que necesitará el motor (promedios ponderados exponenciales, ventanas temporales).
- Robusto, open source, con soporte de larga duración.

### 5.3 Frontend Web — Next.js 14 + TypeScript + Tailwind CSS

**Justificación:**
- Next.js es el estándar de la industria para React. App Router (v14) permite SSR, rutas anidadas y layouts.
- TypeScript comparte tipos con la librería de cliente de la API (generada automáticamente desde OpenAPI).
- Tailwind CSS elimina la necesidad de escribir CSS personalizado en la fase inicial.

### 5.4 App Móvil — React Native + Expo (SDK 51+)

**Justificación:**
- Comparte el ecosistema TypeScript/React con el frontend web, reduciendo la curva de aprendizaje.
- Expo permite builds sin necesidad de Xcode/Android Studio en desarrollo.
- Expo Go permite pruebas en dispositivo físico sin publicar en stores.
- Para distribución self-hosted: EAS Build genera APK/IPA descargables.

### 5.5 Cache y mensajería — Redis 7

**Justificación:**
- Broker de tareas para Celery (sincronización periódica).
- Cache de planes de entrenamiento calculados (evita recalcular en cada petición).
- Almacenamiento de sesiones JWT en lista negra (invalidación de tokens).

### 5.6 Tareas asíncronas — Celery 5

**Justificación:**
- La sincronización con intervals.icu no debe bloquear el hilo principal.
- Permite programar sincronizaciones periódicas (cada 6 horas, por ejemplo) sin infraestructura adicional.
- Reintento automático en caso de fallo de red.

### 5.7 LLM local opcional — Ollama

**Justificación:**
- Permite usar modelos como Llama 3, Mistral o Phi-3 de forma completamente local y gratuita.
- Se usa exclusivamente para generar descripciones en lenguaje natural de los entrenamientos. La lógica de planificación **no depende** del LLM.
- Si Ollama no está disponible, el sistema funciona con descripciones predefinidas (degradación elegante).

### 5.8 Contenedores — Docker + Docker Compose

**Justificación:**
- Un único `docker-compose.yml` levanta toda la infraestructura.
- Reproduciblidad total en cualquier SO con Docker instalado (Windows/WSL, macOS, Linux).
- Volúmenes nombrados para persistencia de datos entre reinicios.

### Resumen del stack

| Capa | Tecnología | Versión objetivo |
|---|---|---|
| Backend API | Python + FastAPI | 3.12 / 0.111+ |
| ORM | SQLAlchemy + Alembic | 2.0 / 1.13+ |
| Validación | Pydantic | v2 |
| Base de datos | PostgreSQL + TimescaleDB | 16 / 2.14 |
| Cache/Queue | Redis | 7.2 |
| Workers | Celery | 5.4 |
| Web Frontend | Next.js + TypeScript | 14 / 5.x |
| CSS | Tailwind CSS | 3.4 |
| Móvil | React Native + Expo | 0.74 / SDK 51 |
| LLM local | Ollama | última estable |
| Contenedores | Docker + Compose | 25+ / v2 |

---

## 6. Modelo de Datos

> Nota: Este es el modelo conceptual inicial. El esquema SQL se definirá en detalle en la Fase 1.

### 6.1 Entidades principales

```
users
├── id (UUID)
├── email (unique)
├── hashed_password
├── created_at
└── is_active

user_profiles
├── id (UUID)
├── user_id (FK → users)
├── intervals_athlete_id
├── intervals_api_key_encrypted
├── training_days (JSON)        -- [{"day": "MON", "available_hours": 1.5}, ...]
├── active_sports (JSON)        -- ["running", "cycling", "swimming_pool", "swimming_open"]
├── strength_training (bool)    -- El usuario quiere incluir fuerza en el plan
├── gym_access (bool)           -- Tiene acceso a gimnasio con pesas/máquinas
├── has_power_meter (bool)
├── pool_access (bool)
├── pool_lane_meters (int)      -- 25 o 50 metros
├── pool_days_per_week (int)
├── experience_level (JSON)     -- {"running": "intermediate", "cycling": "beginner", ...}
└── last_sync_at

physiological_data
├── id (UUID)
├── user_id (FK → users)
├── recorded_at (date)
├── max_hr (bpm)
├── resting_hr (bpm)
├── ftp_watts (nullable)        -- Solo si tiene potenciómetro en bici
├── lthr_bpm (nullable)         -- Umbral de lactato por FC (running y ciclismo)
├── vo2max_estimated (nullable)
├── css_sec_per_100m (nullable) -- Critical Swim Speed: ritmo umbral en natación (seg/100m)
├── hr_zones_running (JSON)     -- Zonas FC calculadas para running
├── hr_zones_cycling (JSON)     -- Zonas FC calculadas para ciclismo
├── hr_zones_swimming (JSON)    -- Zonas FC para natación (si se usa FC en agua)
└── power_zones (JSON)          -- Calculadas de FTP (si aplica)

goals
├── id (UUID)
├── user_id (FK → users)
├── sport                       -- cycling / running / swimming_pool / swimming_open / triathlon / duathlon
├── goal_type                   -- distance_event / time_event / triathlon_format / duathlon_format / performance
├── triathlon_format            -- sprint / olympic / half_ironman / ironman / custom (solo si sport=triathlon)
├── duathlon_format             -- sprint / standard / long / custom (solo si sport=duathlon)
├── name
├── target_date (date)
├── target_distance_km (nullable)
├── target_time_seconds (nullable)
├── triathlon_swim_km (nullable)
├── triathlon_bike_km (nullable)
├── triathlon_run_km (nullable)
├── duathlon_run1_km (nullable)
├── duathlon_bike_km (nullable)
├── duathlon_run2_km (nullable)
├── priority                    -- A / B / C
├── is_active (bool)
└── created_at

activities                      -- Hypertable TimescaleDB (time: started_at)
├── id (UUID)
├── user_id (FK → users)
├── intervals_activity_id       -- ID original en intervals.icu
├── started_at (timestamptz)
├── sport_type                  -- cycling / running / swimming_pool / swimming_open / virtual_ride / trail_run / other
├── duration_seconds
├── distance_meters
├── elevation_gain_meters
├── avg_hr (bpm, nullable)
├── max_hr (bpm, nullable)
├── avg_power_watts (nullable)  -- Solo si tiene potenciómetro
├── normalized_power (nullable)
├── tss                         -- Training Stress Score calculado
├── pool_length_meters (nullable)       -- 25 o 50, solo para swimming_pool
├── swolf_avg (nullable)                -- Solo natación: eficiencia técnica
├── strokes_per_length_avg (nullable)   -- Solo natación
├── pace_per_100m_sec (nullable)        -- Solo natación: ritmo medio
├── raw_data (JSONB)            -- Datos completos de intervals.icu
└── synced_at

wellness_data                   -- Hypertable TimescaleDB (time: recorded_date)
├── id (UUID)
├── user_id (FK → users)
├── recorded_date (date)
-- Campos CONFIRMADOS disponibles en intervals.icu API (verificado 2026-04-21):
├── ctl                         -- Crónico (fitness) — proporcionado directamente por intervals
├── atl                         -- Agudo (fatiga) — proporcionado directamente por intervals
├── ramp_rate                   -- Tasa de cambio del CTL
├── resting_hr (nullable)       -- FC en reposo (bpm) — campo: "restingHR"
├── hrv_rmssd (nullable)        -- HRV en ms — campo: "hrv" (RMSSD). hrvSDNN no disponible
├── hrv_status                  -- within_swc/outside_swc — CALCULADO por TrainSync, no viene de API (ver 8.4)
├── sleep_secs (nullable)       -- Duración sueño en segundos — campo: "sleepSecs"
├── sleep_score (nullable)      -- 0-100 — campo: "sleepScore"
├── sleep_quality (nullable)    -- Escala entera — campo: "sleepQuality"
├── spo2 (nullable)             -- Saturación de oxígeno — campo: "spO2"
├── steps (nullable)            -- Pasos del día — campo: "steps"
-- Campos presentes en API pero NULL en este atleta (no usar hasta confirmar disponibilidad):
-- weight, kcalConsumed, avgSleepingHR, soreness, fatigue, stress, mood,
-- motivation, injury, respiration, hrvSDNN, readiness, vo2max, lactate
└── raw_data (JSONB)            -- Respuesta completa de intervals para auditoría

training_plans
├── id (UUID)
├── user_id (FK → users)
├── goal_id (FK → goals, nullable)
├── generated_at (timestamptz)
├── valid_for_date (date)
├── plan_data (JSONB)           -- Sesiones planificadas para los próximos días
└── recalculation_reason        -- initial / daily / wellness_change / goal_change

onboarding_tests
├── id (UUID)
├── user_id (FK → users)
├── test_type                   -- max_hr_test / ftp_ramp_test / lthr_test / 20min_ftp
├── sport
├── status                      -- pending / completed / skipped
├── assigned_at
└── completed_at
```

---

## 7. Integración con intervals.icu

### 7.1 Documentación de referencia

- URL base API: `https://intervals.icu/api/v1`
- Autenticación: HTTP Basic Auth con usuario `API_KEY` y contraseña = la clave del usuario
- Documentación oficial: `https://intervals.icu/api/v1/docs` (requiere login)

### 7.2 Endpoints principales a consumir

| Endpoint | Uso en TrainSync |
|---|---|
| `GET /athlete/{id}` | Perfil del atleta, datos básicos |
| `GET /athlete/{id}/activities.csv` | Lista de actividades (CSV, más eficiente que JSON) |
| `GET /athlete/{id}/activity/{actId}` | Detalle completo de una actividad |
| `GET /athlete/{id}/wellness?oldest=YYYY-MM-DD&newest=YYYY-MM-DD` | Wellness diario |
| `GET /athlete/{id}/events` | Eventos/objetivos definidos en intervals |
| `GET /athlete/{id}/fitnesschart` | CTL, ATL históricos calculados por intervals |

> **Nota de implementación:** intervals.icu ya calcula CTL/ATL directamente en el endpoint de wellness. Usaremos estos valores en lugar de recalcularlos, ya que tienen el historial completo del atleta desde antes de TrainSync.

### 7.3 Campos de wellness confirmados disponibles (verificado 2026-04-21 con cuenta real)

| Campo API | Nombre interno | Disponible | Notas |
|---|---|---|---|
| `ctl` | ctl | Si | Calculado por intervals, 42d EWA |
| `atl` | atl | Si | Calculado por intervals, 7d EWA |
| `rampRate` | ramp_rate | Si | Tasa cambio CTL |
| `restingHR` | resting_hr | Si | FC reposo en bpm |
| `hrv` | hrv_rmssd | Si | RMSSD en ms (hrvSDNN no disponible) |
| `sleepSecs` | sleep_secs | Si | Duracion en segundos |
| `sleepScore` | sleep_score | Si | Score 0-100 |
| `sleepQuality` | sleep_quality | Si | Entero |
| `spO2` | spo2 | Si | Saturacion oxigeno % |
| `steps` | steps | Si | Pasos diarios |
| `weight` | — | Null | Vacío si el atleta no registra su peso |
| `hrvSDNN` | — | Null | No disponible |
| `readiness` | — | Null | No disponible |
| `vo2max` | — | Null | No disponible via wellness |

### 7.4 Campos de actividades confirmados disponibles

Confirmados en el CSV de actividades:
- `icu_training_load` = TSS de la sesion
- `icu_fitness` = CTL en el momento de la actividad
- `icu_fatigue` = ATL en el momento de la actividad
- `average_heartrate`, `max_heartrate`
- `hr_z1_secs` hasta `hr_z7_secs` = segundos en cada zona
- `lthr` = LTHR del atleta
- `hr_max` = FC maxima del atleta
- `icu_average_watts`, `icu_normalized_watts` = vacios si no hay potenciometro
- `icu_ftp` = FTP guardado (puede ser un valor manual, sin medicion real)

### 7.5 Perfil de ejemplo para desarrollo y pruebas

Valores ficticios con los que se escriben los tests y los ejemplos de este documento. No corresponden a ningún atleta real; la columna Fuente indica de dónde los toma TrainSync en una cuenta real:

| Dato | Valor de ejemplo | Fuente |
|---|---|---|
| FC maxima | 192 bpm | Campo `hr_max` en actividades |
| FC reposo | 46 bpm | Campo `restingHR` en wellness |
| LTHR (umbral) | 174 bpm | Campo `lthr` en actividades |
| Peso | — | Campo `icu_weight` en actividades |
| CTL | ~40 | Campo `icu_fitness` |
| Potenciometro | No | Campos de potencia vacios en actividades |
| FTP guardado | Valor manual, sin medición real | Campo `icu_ftp` |
| Deportes registrados | Run, Ride, TrailRun, VirtualRide, Walk, Hike | Actividades CSV |

> **Nota sobre TSB:** intervals.icu no devuelve TSB directamente en el endpoint de wellness. TrainSync lo calcula como `TSB = CTL - ATL` usando los campos `ctl` y `atl` del endpoint de wellness.

```
Sincronización inicial (al conectar cuenta):
  → Cargar historial completo de actividades (últimos 12 meses)
  → Cargar historial completo de wellness (últimos 90 días)
  → Calcular CTL/ATL/TSB histórico o usar el de intervals

Sincronización periódica (Celery beat, cada 6 horas):
  → Cargar actividades desde la última sincronización
  → Cargar wellness desde la última sincronización
  → Recalcular métricas de carga si hay datos nuevos
  → Invalidar cache del plan si los datos cambiaron

Sincronización manual (petición del usuario):
  → Igual que la periódica, ejecución inmediata
```

### 7.4 Manejo de datos faltantes

Si en la sincronización inicial se detecta que faltan datos críticos:

| Dato faltante | Acción del sistema |
|---|---|
| FC máxima | Proponer test de campo: carrera o ciclismo con esfuerzo máximo progresivo |
| LTHR running | Proponer test de 30 minutos a esfuerzo máximo sostenible corriendo |
| LTHR ciclismo | Proponer test de 30 minutos a esfuerzo máximo sostenible en bici |
| FTP (si tiene potenciómetro) | Proponer Ramp Test o test de 20 minutos |
| CSS natación | Proponer test 400m + 200m a máximo esfuerzo, o test 1500m contrarreloj |
| Sin actividades históricas | Marcar como perfil nuevo, usar valores poblacionales como punto de partida |
| Sin datos HRV | Mostrar advertencia, generar plan sin ajuste HRV hasta tener datos |

---

## 8. Motor de Entrenamiento

> Este es el núcleo del proyecto. Se implementará progresivamente en las fases 2 y 3.

### 8.0 Cómo funciona la planificación — Visión general

El motor trabaja con tres "medidores" que describen el estado del atleta en cada momento:

**CTL — Fitness acumulado (ventana 42 días)**
Representa la carga de entrenamiento crónica. Sube lentamente con meses de entrenamiento consistente y baja lentamente si se deja de entrenar. Es el "fondo de forma" del atleta.

**ATL — Fatiga reciente (ventana 7 días)**
Representa la carga de entrenamiento aguda. Sube rápido tras una semana dura y baja rápido con el descanso. Refleja el cansancio acumulado a corto plazo.

**TSB — Forma del día (CTL − ATL)**
Es la resta de los dos anteriores. TSB positivo = atleta fresco con buen fondo. TSB negativo = atleta cargado. Los atletas llegan a competición con TSB positivo reduciendo la carga la última semana (taper).

Ejemplo con el perfil de ejemplo (§7.5):
- CTL ≈ 40 → fitness moderado-bueno tras meses de entrenamiento
- ATL ≈ 45 → cargado, acaba de competir (una carrera de 16 km)
- TSB ≈ −5 → ligeramente fatigado, estado normal post-competición

**Proceso de decisión diaria**

Cada mañana el sistema ejecuta este proceso en orden:

```
PASO 1 — Leer estado del día (datos de intervals.icu sincronizados)
  → CTL, ATL (de wellness endpoint)
  → TSB = CTL - ATL
  → HRV: media móvil 7 días de ln(RMSSD) vs SWC personal (status: within_swc/outside_swc)
  → Sleep score y duración de sueño
  → Actividades registradas ayer (¿se entrenó? ¿TSS real vs planificado?)

PASO 2 — Determinar capacidad de carga del día
  → Combinar TSB + HRV status + sleep score en un score compuesto
  → Score alto   = día de carga (sesión de calidad o volumen)
  → Score medio  = día de base (aeróbico moderado)
  → Score bajo   = día de recuperación o descanso activo

PASO 3 — Determinar fase del plan
  → Calcular semanas restantes hasta el objetivo prioritario
  → Asignar fase: BASE / CONSTRUCCIÓN / PEAK / TAPER
  → Dentro de la fase, determinar semana de carga o semana de recuperación
    (patrón típico: 3 semanas de carga + 1 semana de descarga)

PASO 4 — Seleccionar tipo de sesión
  → Cruzar fase + deporte que toca según distribución semanal + capacidad del día
  → Tipos posibles: recuperación, aeróbico base, tempo, umbral,
                    intervalos VO2max, anaeróbico, tirada larga, fuerza

PASO 5 — Construir sesión principal y alternativas
  → Sesión principal: deporte prioritario según objetivo activo
  → Alternativas: misma carga y tipo de sesión, adaptada a otros deportes activos
  → Si fuerza está activa y corresponde por fase: incluir como opción de sesión
```

**El papel del entrenamiento de fuerza en la planificación**

La fuerza genera fatiga real que el sistema debe contabilizar. Si no se incluye, el motor puede proponer una sesión de calidad el día después de una sesión intensa de piernas, lo cual sería contraproducente. Además debe programarse de forma estratégica: tiene más peso en las fases iniciales y se reduce progresivamente hacia la competición.

---

### 8.1 Métricas de carga de entrenamiento

**TSS — Training Stress Score** (carga de una sesión aeróbica)

Para sesiones basadas en frecuencia cardíaca (sin potenciómetro):
```
hrTSS = (duracion_seg × HR_ratio² × 100) / 3600
HR_ratio = (HR_media - HR_reposo) / (LTHR - HR_reposo)
```

Para sesiones basadas en potencia — ciclismo con potenciómetro:
```
TSS = (duracion_seg × NP × IF) / (FTP × 3600) × 100
IF = NP / FTP
NP = Normalized Power (media cuadratica de potencia en ventana 30s)
```

**CTL — Chronic Training Load** (fitness, ventana 42 días)
```
CTL_hoy = CTL_ayer + (TSS_hoy - CTL_ayer) × (1 - e^(-1/42))
```

**ATL — Acute Training Load** (fatiga, ventana 7 días)
```
ATL_hoy = ATL_ayer + (TSS_hoy - ATL_ayer) × (1 - e^(-1/7))
```

**TSB — Training Stress Balance** (forma)
```
TSB = CTL - ATL

TSB > +25    → demasiado fresco (fuera de taper: se está perdiendo forma)
TSB +5/+25   → fresco; objetivo A el día de carrera: +15/+25
TSB -10/+5   → neutro, entrenamiento normal
TSB -30/-10  → zona productiva de carga, entrenamiento normal
TSB < -30    → riesgo alto, solo recuperación
```

> Umbrales según Joe Friel (TrainingPeaks). TSB en valor absoluto, no en % del CTL. Versión 0.4: antes, TSB < −10 reducía la carga; se eliminó porque −10/−30 es la zona en la que se construye forma.

**rTSS — Fuerza (Resistance TSS)**

intervals.icu no calcula carga para sesiones de fuerza. TrainSync la estima mediante RPE y duración:
```
rTSS = (RPE / 10)² × (duracion_min / 60) × 100

Ejemplo: sesión de fuerza de 60 min con RPE 7
rTSS = (7/10)² × (60/60) × 100 = 0.49 × 1 × 100 = 49

Escala RPE:
1-3  → esfuerzo muy ligero (movilidad, activación)
4-6  → esfuerzo moderado (hipertrofia, trabajo técnico)
7-8  → esfuerzo alto (fuerza máxima, trabajo intenso)
9-10 → esfuerzo máximo (test de 1RM, fatiga extrema)
```

> El rTSS se suma al TSS aeróbico del día para calcular el ATL y CTL total. El usuario introduce el RPE percibido tras la sesión desde la app. Si no lo introduce, el sistema usa el RPE planificado como estimación.

### 8.2 Cálculo de TSS por deporte

**Running y ciclismo sin potenciómetro — hrTSS**
```
hrTSS = (duracion_seg × HR_ratio² × 100) / 3600
HR_ratio = (HR_media - HR_reposo) / (LTHR - HR_reposo)
```

**Ciclismo con potenciómetro — TSS estándar**
```
TSS = (duracion_seg × NP × IF) / (FTP × 3600) × 100
IF = NP / FTP
NP = Normalized Power (media cuadratica de potencia en ventana 30s)
```

**Natación — sTSS (Swimming TSS)**
```
sTSS = (duracion_seg × ritmo_ratio² × 100) / 3600
ritmo_ratio = CSS / ritmo_medio_100m
CSS = Critical Swim Speed (ritmo umbral en seg/100m)
```

> CSS es el equivalente al LTHR/FTP para natación. Se obtiene con el test de 400m + 200m a máximo esfuerzo o con el test de 1500m contrarreloj. Si el usuario no tiene CSS, el sistema propone el test de campo correspondiente.

### 8.3 Zonas de entrenamiento

**Zonas por Frecuencia Cardíaca** (basadas en LTHR, tablas de Joe Friel; distintas por deporte):

| Zona | Nombre | Running % LTHR | Ciclismo % LTHR |
|---|---|---|---|
| Z1 | Recuperación | < 85% | < 81% |
| Z2 | Aeróbica | 85–89% | 81–89% |
| Z3 | Tempo | 90–94% | 90–93% |
| Z4 | Umbral | 95–99% | 94–99% |
| Z5 | VO2max | 100–106% | 100–106% |
| Z6 | Anaeróbica | > 106% | > 106% |

> Las sesiones fáciles (recuperación, aeróbico, larga) llevan además un techo de `LTHR − 30` ppm, estimación de Friel del umbral aeróbico, hasta que el usuario lo mida.

**Zonas de Potencia — Ciclismo con potenciómetro** (basadas en FTP):

| Zona | Nombre | % FTP |
|---|---|---|
| Z1 | Recuperación Activa | < 55% |
| Z2 | Resistencia | 56–75% |
| Z3 | Tempo | 76–90% |
| Z4 | Umbral de Lactato | 91–105% |
| Z5 | VO2max | 106–120% |
| Z6 | Capacidad Anaeróbica | 121–150% |
| Z7 | Potencia Neuromuscular | > 150% |

**Zonas de Natación** (basadas en CSS, ritmo por 100m):

| Zona | Nombre | % CSS (ritmo) |
|---|---|---|
| Z1 | Recuperación | > 120% CSS (más lento) |
| Z2 | Base aeróbica | 110–120% CSS |
| Z3 | Tempo | 103–110% CSS |
| Z4 | Umbral (CSS) | 97–103% CSS |
| Z5 | VO2max | 90–97% CSS |
| Z6 | Anaeróbica | < 90% CSS |

> En natación el ritmo es inverso: un % CSS más bajo significa nadar más rápido que el umbral.

### 8.4 Modelo de planificación — Polarizado 80/20 con desarrollo temprano de VO2max

**Distribución de intensidad:**
- **~80% del volumen semanal** en Zona 1-2 (baja intensidad, aeróbica)
- **~20% del volumen semanal** en Zona 4-5
- **Zona 3 (tempo) evitada activamente** — ver nota sobre la zona gris más abajo
- **Clasificación (v0.4):** Z4 (por debajo de la LTHR) cuenta como intensidad **moderada**, Z5-Z6 como **alta**. En el modelo de 3 zonas de Seiler, la Z4 queda entre los dos umbrales, así que un plan con sesiones de umbral es piramidal. Se mantiene porque piramidal y polarizado rinden de forma parecida y ambos superan al modelo de umbral (Oliveira 2024).

Esta distribución se aplica a los tres deportes aeróbicos (running, ciclismo, natación) y también guía la programación del fuerza como bloque complementario.

**VO2max al inicio de la preparación**

El VO2max es el techo del sistema aeróbico. Trabajarlo solo en la fase final (cuando ya hay mucha fatiga acumulada) limita los resultados. La estrategia adoptada es introducir bloques cortos de trabajo de VO2max desde la fase BASE temprana, antes de que el volumen suba:

```
Fase BASE temprana (primeras 2-4 semanas):
  → 75% Z1-Z2 (aeróbico suave, base)
  → 25% Z5 (intervalos cortos de VO2max: ej. 5×3min, 8×2min)
  → Objetivo: elevar el techo aeróbico sin acumular fatiga de volumen

Fase BASE tardía:
  → 85% Z1-Z2 (volumen creciente)
  → 15% Z4-Z5
  → Objetivo: construir base con techo ya desarrollado

Fase CONSTRUCCIÓN:
  → 80% Z1-Z2
  → 20% Z4 (umbral) con algo de Z5 de mantenimiento
  → Objetivo: volumen + calidad específica al objetivo

Fase PEAK:
  → 75-80% Z1-Z2
  → 20-25% Z4-Z5 específico al evento objetivo
  → Objetivo: máximo estímulo específico

Fase TAPER:
  → Reducción de volumen ~40-50%
  → Mantener intensidad (Z4-Z5 breve)
  → Objetivo: llegar fresco con TSB positivo
```

**La zona gris — Qué es y por qué el motor la evita activamente**

La zona gris es la Z3 (tempo). Es suficientemente intensa para generar fatiga real, pero no suficientemente intensa para producir las adaptaciones fisiológicas de la Z4-Z5. Entrenar habitualmente en Z3 supone pagar el coste de recuperación sin obtener el beneficio, y es el error más frecuente en atletas sin estructura que entrenan "a ritmo cómodo-rápido".

**Implicaciones técnicas concretas para el motor:**

```
REGLA 1 — Las sesiones generadas nunca tienen Z3 como objetivo principal.
  → Sesión fácil = Z1-Z2 puro. Sesión de calidad = Z4-Z5.
  → Z3 solo aparece como elemento puntual dentro de sesiones de umbral progresivo
    o en semanas de peak muy específicas (ej: simulacros de ritmo de carrera).

REGLA 2 — Detección de zona gris en actividades registradas.
  → Tras sincronizar una actividad, el sistema analiza la distribución de zonas.
  → Si Z3 > 15% del tiempo total de la sesión en una sesión "fácil" planificada:
      → Se registra como alerta de "sesión demasiado intensa"
      → Se tiene en cuenta en el recálculo del día siguiente (más fatiga de lo esperado)
      → Se muestra al usuario como feedback: "Esta sesión entró en zona gris"

REGLA 3 — Si el análisis semanal detecta patrón de zona gris recurrente:
  → El sistema avisa al usuario y ajusta las propuestas para anclar mejor la Z2.
```

**Distribución del volumen entre deportes para triatlón y duatlón**

[Inferencia, basada en literatura general de triatlón y duatlón]: proporciones aproximadas orientativas, ajustables según debilidades del atleta.

Triatlón:
| Formato | Natación | Ciclismo | Running |
|---|---|---|---|
| Sprint | 10% | 55% | 35% |
| Olímpico | 10% | 55% | 35% |
| 70.3 | 10% | 55% | 35% |
| Ironman | 10% | 55% | 35% |

Duatlón:
| Formato | Running total | Ciclismo |
|---|---|---|
| Todos | 40% | 60% |

**Cálculo de HRV status (ln RMSSD vs SWC personal)** — v0.4

intervals.icu no proporciona un estado HRV categorizado. TrainSync usa el método de los estudios de entrenamiento guiado por HRV (Vesterinen 2016, Javaloyes 2019) y de la guía del Australian Institute of Sport:

```
ln_rmssd   = ln(RMSSD de cada mañana)
rolling7   = media de ln_rmssd de los últimos 7 días (mínimo 3 valores)
baseline   = media de ln_rmssd de los 28 días anteriores (mínimo 14 valores)
swc        = 0,5 × desviación típica de ln_rmssd en esos 28 días

|rolling7 - baseline| <= swc  → "within_swc"   (plan normal)
en otro caso                  → "outside_swc"  (baja intensidad, en cualquier dirección)
sin datos suficientes         → sin ajuste por HRV
```

> v0.4 sustituye la regla anterior (valor del día vs media 7 días, ±8 %): el RMSSD diario tiene un coeficiente de variación de ~14,5 % frente a ~6,7 % de la media móvil, y los estudios no respaldan subir la intensidad con HRV "elevado".

**Algoritmo de decision diaria**

Cada día, antes de generar la recomendación, el sistema evalúa en este orden:

```
1. TSB actual (CTL - ATL de intervals.icu)
   TSB >= -30 → seguir plan
   TSB < -30  → riesgo alto, solo recuperacion

2. HRV status (calculado segun formula anterior)
   "within_swc"  → ejecutar sesion segun plan
   "outside_swc" → sesion de calidad pasa a aerobica suave; con sueño < 50, recuperacion

3. Sleep score (campo sleepScore de intervals.icu)
   < 50  → reducir carga del dia ~30%, evitar sesiones de calidad
   50-70 → plan normal
   > 70  → plan normal o ligeramente elevado

4. Semanas hasta objetivo → determinar fase activa (base/construccion/peak/taper)

5. Dias consecutivos de entrenamiento
   3 o mas dias seguidos → evaluar insercion de descanso activo
```

### 8.5 Periodización hacia objetivos

Para cada objetivo el sistema calculará automáticamente las fases:

```
Total de semanas disponibles → dividir en:
├── Fase BASE (40–50% del tiempo): Aeróbica, volumen bajo/medio, sin intensidad
├── Fase CONSTRUCCIÓN (30–35%): Aumentar volumen + introducir calidad
├── Fase PEAK (15–20%): Máxima carga específica al objetivo
└── Fase TAPER (10–15%): Reducción de carga, mantenimiento de intensidad
```

> v0.5: el taper se fija en días según la prueba (5K/10K ~9, media ~12, maratón y ultra ~21, resto 14) y el resto se reparte con estas proporciones. Sin fecha de inicio, la preparación empieza 20 semanas antes del objetivo. El catálogo distingue running en asfalto por distancia, trail, ciclismo de fondo, natación en piscina y aguas abiertas, triatlón y duatlón. El RPE de intervals.icu (`icu_rpe`, `session_rpe`) entra en el ajuste diario y en la carga semanal (sRPE, monotonía). Detalle y fuentes: `MOTOR_ENTRENAMIENTO.md` v0.4.

---

## 9. Roadmap por Fases

### FASE 0 — Infraestructura y fundamentos ⬅ ACTUAL
**Objetivo:** El proyecto está levantado, conectado y listo para desarrollar.

**Tareas:**
- [ ] Crear estructura de directorios del proyecto
- [ ] Escribir `docker-compose.yml` con todos los servicios
- [ ] Configurar variables de entorno (`.env.example`)
- [ ] Explorar y documentar la API de intervals.icu (endpoints reales, límites de rate)
- [ ] Verificar qué campos de wellness y actividades están disponibles en la cuenta real
- [ ] Crear script de inicialización de base de datos con TimescaleDB
- [ ] Confirmar que `docker compose up` levanta todos los servicios sin errores

**Criterio de éxito:** `docker compose up` → todos los servicios healthy. Swagger UI de FastAPI accesible en `localhost:8000/docs`.

---

### FASE 1 — Backend Core
**Objetivo:** API funcional con autenticación y sincronización de intervals.icu.

**Tareas:**
- [ ] Modelos de base de datos con SQLAlchemy + Alembic
- [ ] Endpoints de autenticación (registro, login, refresh, logout)
- [ ] Cifrado de API keys de intervals.icu
- [ ] Servicio de integración con intervals.icu (cliente HTTP)
- [ ] Tarea Celery de sincronización inicial
- [ ] Tarea Celery de sincronización periódica (beat scheduler)
- [ ] Endpoints REST: `/users`, `/wellness`, `/activities`
- [ ] Tests unitarios del cliente de intervals.icu

**Criterio de éxito:** Un usuario puede registrarse, introducir su API key de intervals.icu, y ver sus actividades y wellness en la API de TrainSync.

---

### FASE 2 — Motor de Entrenamiento v1
**Objetivo:** Cálculo correcto de métricas de carga y zonas. Onboarding de datos fisiológicos.

**Tareas:**
- [ ] Implementar cálculo de TSS (hrTSS y powerTSS)
- [ ] Implementar cálculo de CTL, ATL, TSB con ventanas históricas
- [ ] Implementar cálculo de zonas de FC y potencia
- [ ] Flujo de onboarding: detección de datos faltantes
- [ ] Tests de campo como sesiones de entrenamiento descargables
- [ ] Generador básico de plan semanal (sin ajuste dinámico aún)
- [ ] Endpoint `/plan/today` que devuelve la sesión del día

**Criterio de éxito:** Con datos reales de intervals.icu, el sistema genera una sesión de entrenamiento coherente con el estado de forma actual del usuario.

---

### FASE 3 — Plan Dinámico y Objetivos
**Objetivo:** El plan se adapta diariamente al estado del atleta y proyecta hacia un objetivo.

**Tareas:**
- [ ] Gestión de objetivos (CRUD completo)
- [ ] Lógica de periodización automática basada en semanas disponibles
- [ ] Ajuste diario del plan según HRV, sueño y TSB
- [ ] Generación de plan para los próximos 14 días
- [ ] Recálculo automático del plan al detectar datos nuevos
- [ ] Invalidación de cache cuando cambian los datos de wellness
- [ ] Integración opcional con Ollama para descripciones en lenguaje natural
- [ ] Tests del motor de planificación con datos históricos conocidos

**Criterio de éxito:** El plan refleja coherentemente el estado del atleta. Un día con HRV bajo y mal sueño genera una sesión de recuperación. Una semana de buena forma cerca de un objetivo genera sesiones de calidad.

---

### FASE 4 — Frontend Web
**Objetivo:** Interfaz web completa y usable.

**Tareas:**
- [ ] Layout base con autenticación (login, registro)
- [ ] Dashboard: CTL, ATL, TSB, último sueño, HRV
- [ ] Vista de plan: próximos 14 días
- [ ] Calendario con actividades realizadas vs planificadas
- [ ] Página de objetivos (crear, editar, ver progreso)
- [ ] Configuración de perfil de entrenamiento
- [ ] Onboarding flow para nuevos usuarios
- [ ] Diseño responsive (mobile-first)

**Criterio de éxito:** Un usuario nuevo puede completar el onboarding, ver su estado de forma actual y consultar su plan para los próximos días, todo desde el navegador.

---

### FASE 5 — App Móvil
**Objetivo:** Versión móvil funcional equivalente al web.

**Tareas:**
- [ ] Setup Expo con TypeScript
- [ ] Shared API client entre web y móvil
- [ ] Pantallas: Login, Dashboard, Plan del día, Objetivos
- [ ] Notificaciones push: recordatorio de entrenamiento del día
- [ ] Build APK para Android (self-hosted via EAS o local)
- [ ] Build IPA para iOS (requiere cuenta Apple Developer)

**Criterio de éxito:** La app es instalable en un dispositivo Android y muestra el plan del día correctamente.

---

### FASE 6 — Calidad, Tests y Documentación
**Objetivo:** El proyecto es mantenible y documentado.

**Tareas:**
- [ ] Cobertura de tests unitarios > 80% en el motor de entrenamiento
- [ ] Tests de integración de los endpoints principales
- [ ] README completo con instrucciones de instalación
- [ ] Guía de contribución
- [ ] Documentación de la API (OpenAPI ya generado por FastAPI)
- [ ] Guía de self-hosting

---

## 10. Estructura de Directorios del Proyecto

```
trainsync/
├── docker-compose.yml
├── docker-compose.dev.yml          # Overrides para desarrollo
├── .env.example
├── .gitignore
│
├── backend/
│   ├── Dockerfile
│   ├── pyproject.toml
│   ├── alembic/                    # Migraciones de base de datos
│   │   ├── env.py
│   │   └── versions/
│   └── app/
│       ├── main.py                 # Entrada FastAPI
│       ├── config.py               # Variables de entorno
│       ├── database.py             # Conexión SQLAlchemy
│       ├── models/                 # Modelos SQLAlchemy
│       ├── schemas/                # Esquemas Pydantic
│       ├── routers/                # Endpoints por dominio
│       │   ├── auth.py
│       │   ├── users.py
│       │   ├── activities.py
│       │   ├── wellness.py
│       │   ├── goals.py
│       │   └── plan.py
│       ├── services/
│       │   ├── intervals_client.py # Cliente HTTP intervals.icu
│       │   ├── training_engine.py  # Motor de planificación
│       │   ├── crypto.py           # Cifrado de API keys
│       │   └── llm_client.py       # Cliente Ollama (opcional)
│       ├── tasks/                  # Tareas Celery
│       │   ├── celery_app.py
│       │   ├── sync_tasks.py
│       │   └── plan_tasks.py
│       └── tests/
│
├── prototype/web/
│   ├── Dockerfile
│   ├── package.json
│   ├── next.config.ts
│   ├── tailwind.config.ts
│   └── src/
│       ├── app/                    # Next.js App Router
│       ├── components/
│       ├── lib/
│       │   ├── api-client.ts       # Cliente fetch para el backend
│       │   └── types.ts            # Tipos compartidos
│       └── hooks/
│
├── mobile/
│   ├── package.json
│   ├── app.json                    # Config Expo
│   └── src/
│       ├── app/                    # Expo Router
│       ├── components/
│       └── lib/
│           └── api-client.ts       # Mismo cliente que web
│
└── docs/
    ├── MEMORIA_TECNICA.md          # Este documento
    ├── intervals-api-notes.md      # Notas de exploración de la API
    └── training-science.md         # Fuentes y fórmulas del motor
```

---

## 11. Decisiones de Diseño Registradas

| ID | Decisión | Justificación | Alternativas descartadas |
|---|---|---|---|
| DD-01 | Python + FastAPI para backend | Ecosistema científico, async, OpenAPI automático | Node.js/NestJS (peor para cálculos científicos), Go (curva de aprendizaje alta) |
| DD-02 | TimescaleDB sobre InfluxDB | Compatible 100% con PostgreSQL, no requiere aprender otro query language | InfluxDB (ecosistema separado), MongoDB (no relacional, peor para joins) |
| DD-03 | React Native + Expo sobre Flutter | Reutiliza ecosistema React/TypeScript del frontend web | Flutter (mejor rendimiento, pero Dart es otro lenguaje completo) |
| DD-04 | Celery + Redis sobre FastAPI BackgroundTasks | Los background tasks de FastAPI no sobreviven reinicios, no permiten scheduling | FastAPI BackgroundTasks (limitado), APScheduler (menos robusto) |
| DD-05 | Ollama opcional, lógica core sin LLM | El motor de entrenamiento no puede depender de un LLM para ser confiable y predecible | Depender completamente de LLM (no determinista, lento, requiere hardware) |
| DD-06 | Multi-tenant desde el inicio | Rediseñar para multi-usuario posteriormente es muy costoso | Empezar monousuario y migrar después |
| DD-07 | sTSS basado en CSS para natación | Es la métrica más estandarizada y comparable con hrTSS/TSS. No requiere hardware adicional | TRIMP adaptado, zonas de FC en agua (menos preciso sin banda de FC sumergible) |
| DD-08 | Propuesta diaria: 1 principal + alternativas por deporte activo | Permite flexibilidad sin perder la intención del plan. El usuario decide, el sistema registra | Solo mostrar la sesión principal (demasiado rígido), mostrar todas igual (sin dirección clara) |
| DD-09 | Deportes activos configurables en onboarding | No tiene sentido mostrar alternativas de natación a alguien sin acceso a piscina | Mostrar siempre todos los deportes y que el usuario ignore lo que no aplica |
| DD-11 | rTSS basado en RPE × duración para fuerza | intervals.icu no calcula carga para fuerza. RPE es subjetivo pero suficientemente preciso para que la fatiga de fuerza entre en el modelo CTL/ATL | Ignorar la fuerza (plan desajustado), usar TRIMP adaptado (requiere FC, no siempre disponible en gym) |
| DD-12 | Duatlón como tipo de objetivo, no como deporte | El duatlón combina running y ciclismo, que ya son deportes activos. Añadirlo como deporte sería redundante | Añadir "duatlón" como deporte activo separado |
| DD-13 | Next.js 16 + Tailwind CSS 4 en lugar de Next 14 + Tailwind 3.4 | Next 14 ya no recibe soporte de seguridad (2026-10). Tailwind 4 define los tokens del diseño en CSS (`@theme`) | Next 14 (sin parches), Tailwind 3 (config en JS duplicando los tokens) |
| DD-14 | Frontend antes que Fase 1 | Validar el producto con la interfaz antes de fijar el modelo de datos multiusuario; el backend monousuario ya da los datos | Seguir el roadmap original (Fase 1 primero) |

---

## 12. Glosario Técnico

| Término | Definición |
|---|---|
| **Duatlón** | Modalidad multideporte que combina running, ciclismo y running (R1-C-R2). No requiere natación |
| **RPE** | Rate of Perceived Exertion. Escala subjetiva del 1 al 10 del esfuerzo percibido |
| **rTSS** | Resistance Training Stress Score. Carga estimada de una sesión de fuerza: (RPE/10)² × horas × 100 |
| **sTSS** | Swimming Training Stress Score. Carga de una sesión de natación calculada con CSS |
| **SWOLF** | Métrica de eficiencia en natación: suma de tiempo por largo (seg) + brazadas por largo |
| **TSS** | Training Stress Score. Medida de la carga de una sesión de entrenamiento |
| **CTL** | Chronic Training Load. Promedio de TSS de los últimos 42 días. Representa el fitness |
| **ATL** | Acute Training Load. Promedio de TSS de los últimos 7 días. Representa la fatiga |
| **TSB** | Training Stress Balance. CTL - ATL. Representa la forma o frescura del atleta |
| **FTP** | Functional Threshold Power. Potencia máxima sostenible durante 1 hora |
| **LTHR** | Lactate Threshold Heart Rate. Frecuencia cardíaca en el umbral de lactato |
| **NP** | Normalized Power. Potencia normalizada que pondera los esfuerzos variables |
| **IF** | Intensity Factor. NP / FTP. Indica la intensidad relativa de una sesión |
| **HRV** | Heart Rate Variability. Variabilidad de la frecuencia cardíaca. Indicador de recuperación |
| **RMSSD** | Root Mean Square of Successive Differences. Medida común de HRV |
| **Polarized Training** | Modelo de entrenamiento: 80% baja intensidad + 20% alta intensidad |
| **Taper** | Periodo de reducción de carga previo a un evento para llegar en máxima forma |
| **EWA** | Exponential Weighted Average. Promedio ponderado exponencialmente (usado en CTL/ATL) |
| **Hypertable** | Tabla optimizada de TimescaleDB para datos de series temporales |

---

## 13. Control de Cambios del Documento

| Versión | Fecha | Cambios |
|---|---|---|
| 0.1 | 2026-04-21 | Versión inicial. Memoria completa pre-desarrollo |
| 0.5 | 2026-10-19 | Taper por distancia, todos los tipos de prueba y RPE en el motor (ver nota en 8.5 y `MOTOR_ENTRENAMIENTO.md` v0.4). |
| 0.4 | 2026-10-19 | Verificación bibliográfica (detalle en `MOTOR_ENTRENAMIENTO.md` §11.1): TSB solo reduce carga por debajo de −30 (Friel); HRV con media móvil 7 d de ln(RMSSD) vs SWC; zonas de FC distintas para running y ciclismo (Friel) y techo LTHR − 30 en sesiones fáciles; Z4 clasificada como intensidad moderada (plan piramidal). |
| 0.3 | 2026-04-21 | Añadido duatlón como tipo de objetivo (sprint/estándar/long/personalizado). Añadido entrenamiento de fuerza: RF-08, rTSS por RPE×duración, integración en CTL/ATL, programación por fases. Sección 8.0 añadida: explicación completa del proceso de decisión diaria (CTL/ATL/TSB/HRV/sueño → tipo de día → fase → sesión). Modelo de datos actualizado: goals con duathlon_format y campos de distancias, user_profiles con strength_training y gym_access. |

---

> **Próximo paso:** Comenzar la **Fase 0** — Crear la estructura del proyecto y el `docker-compose.yml` base. Antes de empezar, se recomienda explorar la API de intervals.icu con la cuenta real del usuario para verificar qué campos están disponibles en los endpoints de wellness y actividades.
