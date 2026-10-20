# Qué odian los usuarios de TrainingPeaks y Garmin Connect, y qué hace TrainSync con ello

> **Fecha:** 2026-10-20 · Hecho con la skill `replica-entrepreneur` (Jakeschincariol/replica-skill @ 77c9436).
> Las citas son de reseñas reales, copiadas tal cual y enlazadas. Son **investigación, no testimonios**: no se usan en la web ni en anuncios.

## Muestra

| Original | Reseñas usadas | App Store (EE. UU., R. Unido, España) | Hacker News | Periodo |
|---|---|---|---|---|
| TrainingPeaks | 298 | 270 (con estrellas) | 28 menciones | App Store desde 2024-10-20; HN desde 2023 |
| Garmin Connect | 1.436 | 1.363 (con estrellas) | 73 menciones | ídem |

- **Fuentes:** App Store (feed RSS oficial de reseñas de Apple, hasta 500 por país) y Hacker News (API de Algolia). Son **2 fuentes**, por debajo de las 3 que pide la skill.
- **No leídas:** Reddit (su API exige registrar una app con OAuth), Google Play, G2, Capterra y Trustpilot (sin feed oficial; leerlas a mano queda pendiente).
- Por la regla de la skill, un tema con menos de 3 reseñas o con una sola fuente se marca **(fino)**: es una dirección, no una tendencia. Muchos temas salen finos solo porque casi todo viene del App Store.
- Las notas de versión actuales de ambas apps (TP 12.117.0, GC 5.30.1) son genéricas ("bug fixes and performance improvements"): no hay constancia de que hayan arreglado nada de lo de abajo. La reseña más reciente de TP (2026-10-13) sigue quejándose de no poder editar sesiones en el móvil.
- Informes completos: `feedback-tp.md` y `feedback-gc.md`. Recuentos por tema con `themes.json` (temas de la skill + temas de entrenamiento + patrones en español).

## 1. Lo que odian

| # | Problema | Reseñas | Fuentes | Citas |
|---|---|---|---|---|
| 1 | **Garmin Connect+: pagar suscripción después de pagar el reloj** | 53 GC (41 de ≤ 3★) | 2 | "Hardware that costs 1k. Software behind subscription model with MY data." ([App Store ES](https://itunes.apple.com/es/rss/customerreviews/page=6/id=583446403/sortBy=mostRecent/json), 1★) · "En las últimas versiones han cambiado el botón de enviar sesión de entrenamiento al dispositivo por uno de Connect + , pensando en hacer más dinero en lugar de los deportistas." ([App Store ES](https://itunes.apple.com/es/rss/customerreviews/page=1/id=583446403/sortBy=mostRecent/json), 1★) |
| 2 | **TrainingPeaks: la versión gratis es un anuncio de premium** (popups, contenido difuminado, notificaciones-anuncio) | 17 y 14 TP (pueden solaparse) | 1 (fino por fuente) | "This app will interrupt you to sign up for a premium account with almost every single interaction you have with it." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) · "The entire home page at the moment is an ad for premium." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) |
| 3 | **Sincronización e importación que fallan** (reloj ↔ app, Apple Watch, Garmin, Strava) | 54 TP · 234 GC | 2 | "Consistently fails to pull Apple Watch workouts." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) |
| 4 | **Las actualizaciones rompen cosas** (en GC, sobre todo la conexión con el reloj) | 125 GC (121 de ≤ 3★) · 10 TP | 1 (fino por fuente) | "Tired of being Garmin’s alpha tester." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=10/id=583446403/sortBy=mostRecent/json), 1★) |
| 5 | **Lenta, se cuelga, se cierra** | 39 TP · 92 GC | 2 | "it still takes 10-15 seconds for the app to load a workout (which is just text)." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) |
| 6 | **Interfaz confusa o rediseño peor** | 19 TP · 71 GC | 2 | "I feel like I need an instruction manual to use this app." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=3/id=408047715/sortBy=mostRecent/json), 1★) |
| 7 | **Métricas en las que no confían** (sueño, Body Battery, minutos de intensidad) | 16 GC · 12 TP | 1–2 (fino) | "The body battery is very unrealistic." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=8/id=583446403/sortBy=mostRecent/json), 1★) |
| 8 | **Soporte que no responde o es un bot** | 15 TP · 16 GC | 2 / 1 | "Basically all the responses are from an AI bot called Sophie." ([App Store GB](https://itunes.apple.com/gb/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 2★) |

## 2. Lo que falta

| # | Petición | Reseñas | Fuentes | Citas |
|---|---|---|---|---|
| 1 | **Planificar y editar sesiones desde el móvil** (TP obliga a usar la web y, en el móvil, redirige a una app que no deja editar) | 19 TP | 1 (fino por fuente) | "You still can’t edit a workout from the phone in 2026." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 2★, 2026-10-13) · "It reeks of "you use the desktop version to do planning and the mobile version just for recording."" ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) |
| 2 | **Mover una sesión a otro día sin pagar** | 5 TP (lectura manual) | 1 (fino) | "Want to charge me extra to move a workout from one day to another." ([App Store GB](https://itunes.apple.com/gb/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) · "Used to be able to move training and lots of other features on the free version, now only available on premium" ([App Store GB](https://itunes.apple.com/gb/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) |
| 3 | **Un plan que se adapte a tu semana y reconozca lo que hiciste** | 11 GC · 6 TP | 1–2 (fino) | "The plans can’t be adapted to your lofe such as days you can train and days you can’t" ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=583446403/sortBy=mostRecent/json), 1★) · "Garmin coach regularly fails to recognise runs and adjusts training plans assuming you took a rest day" ([App Store GB](https://itunes.apple.com/gb/rss/customerreviews/page=3/id=583446403/sortBy=mostRecent/json), 1★) |
| 4 | **Ver tus datos sin conexión** | 17 GC | 2 | "you have to have internet connection to see anything." ([Hacker News](https://news.ycombinator.com/item?id=47010420)) · "Please add an offline mode." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=6/id=583446403/sortBy=mostRecent/json), 1★) |
| 5 | **Español completo** | 8 TP | 1 (fino) | "no me deja cambiar el idioma a español" ([App Store ES](https://itunes.apple.com/es/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★) · "la versión web únicamente en inglés.." ([App Store ES](https://itunes.apple.com/es/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 3★) |

## 3. Lo que nadie resuelve (posicionamiento)

1. **Quien ya pagó el hardware y no quiere pagar otra vez por sus propios datos.** Es la queja de pago más repetida de Garmin Connect; las reseñas sobre Connect+ empiezan a finales de marzo de 2025. Y la versión gratis de TrainingPeaks no sirve para planificar. 53 reseñas de GC y hasta 31 de TP, 2 fuentes.
2. **Quien está dejando las apps para hacerse su propia herramienta con IA o con intervals.icu.** Aparece en reseñas recientes de ambas apps: "Folks, just build your own tool in Claude." ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=1/id=408047715/sortBy=mostRecent/json), 1★, 2026-10-12) · "Use intervals.icu and move on from this rubbish product if you can" ([App Store US](https://itunes.apple.com/us/rss/customerreviews/page=3/id=408047715/sortBy=mostRecent/json), 2★) · "I created a Garmin CLI that just connects to Garmin Connect. My agent loves it. Now it is my coach" ([Hacker News](https://news.ycombinator.com/item?id=50106116)). **Fino** (pocas reseñas), pero apunta justo a lo que es TrainSync.
3. **Hispanohablantes.** **Fino** (8 reseñas de una sola fuente).

## 4. Plan de mejoras para TrainSync

Elegidas por evidencia × lo barato que es hacerlas. Tamaño: S (días), M (1–2 semanas), L (más).

| # | Qué hacer | Tamaño | Dónde | Evidencia |
|---|---|---|---|---|
| 1 | **Enviar la sesión propuesta al calendario de intervals.icu como entrenamiento estructurado**, para que intervals.icu la lleve al reloj (Garmin, Wahoo, Zwift…). Hoy TrainSync solo lee. [No verificado: que la API de intervals.icu permita crear eventos de tipo entrenamiento con pasos; comprobar antes] | M | backend (`intervals_client.py`, nuevo endpoint) | Sincronización (54 TP, 234 GC, 2 fuentes) y el botón de "enviar al dispositivo" que GC pasó a Connect+ |
| 2 | **Compromiso explícito y comprobable: todo gratis, sin niveles de pago, sin anuncios y sin notificaciones comerciales.** Escribirlo en el README y en la web, y añadirlo a `docs/CUMPLIMIENTO.md` (#9, dark patterns) | S | docs, frontend | Connect+ (53 GC, 2 fuentes) y nagware y muro de pago de TP (hasta 31) |
| 3 | **Mover y cambiar sesiones desde el móvil**, con recálculo del resto de la semana | M | frontend-web (mobile-first) + `/plan` | Planificar en el móvil (19 TP) y mover sesiones (5 TP), ambos finos por fuente |
| 4 | **Días disponibles y emparejado de lo hecho con lo planificado**: el plan respeta los días que marca el usuario (RF-09 ya lo prevé) y explica cuándo da una sesión por hecha | M | motor + frontend | Garmin Coach (11 GC) y TP (6), finos |
| 5 | **Explicar cada propuesta**: mostrar los motivos que ya calcula el motor (`reasons`: TSB, HRV, sueño) y la etiqueta de evidencia de cada regla | S | frontend | Métricas en las que no confían (16 GC, 12 TP), finos |
| 6 | **Web ligera que funcione sin conexión** (PWA con la semana en caché) | M | frontend-web | Lenta (39 TP, 92 GC) y sin conexión (17 GC, 2 fuentes) |
| 7 | **Español completo de serie, incluida la web** | S | frontend (ya en `lang="es"`), textos de sesiones | 8 TP, fino |

Las quejas de precio y cobro no aplican mientras TrainSync sea gratis. Las notificaciones (si llegan en la Fase 5) deben poder desactivarse por tipo.

## 5. Posicionamiento

**A.** Para quien ya pagó su reloj y no quiere pagar otra suscripción para entrenar con sus propios datos, TrainSync planifica gratis, sin anuncios y con código abierto.
Evidencia: suscripción y muro de pago, 53 reseñas de GC y hasta 31 de TP, 2 fuentes.

**B.** Para quien no puede planificar desde el móvil, TrainSync es una web pensada para el móvil donde mueves la sesión de hoy y el plan se recalcula solo.
Evidencia: planificar y mover en el móvil, 24 reseñas de TP, 1 fuente (fino).

**C.** Para quien quiere entender por qué le toca descanso hoy, TrainSync explica cada sesión con tu HRV, tu sueño y tu forma, y cita la evidencia de cada regla.
Evidencia: métricas en las que no confían, 28 reseñas, 2 fuentes (fino por app).

**Recomendada: A.** Es la queja con más reseñas y fuentes, encaja con lo que TrainSync ya es (gratis, AGPL, sobre intervals.icu) y no depende de funciones que aún no existen.

No usar los nombres de TrainingPeaks ni de Garmin en el nombre de la app, anuncios ni ficha de tienda. Una página de comparación con ellos es una cuestión legal que conviene consultar con un abogado en España.
