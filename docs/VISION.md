# Visión — TrainSync

> **Estado:** borrador para revisar con Gabriel · **Fecha:** 2026-10-22
> Ordena todo lo demás: cada apartado, función o cambio debe encajar aquí. Si algo no encaja, o se cambia la visión o no
> se hace. Nace de la revisión de la web del mismo día (`REVISION_2026-10-22.md`).

## 1. Qué es

**El sistema operativo de tu cuerpo:** la plataforma de cuantificación personal y rendimiento deportivo que reúne en un
solo sitio lo que hoy está repartido entre Garmin Connect, Apple Health y Google Health, y que además **te dice qué hacer
hoy y por qué**.

La diferencia con esas apps no es tener más gráficas, es cerrar el círculo: **medir → entender → decidir → hacer → medir**.
Garmin decide pero encierra tus datos en su ecosistema; Apple y Google guardan los datos pero no deciden nada.

## 2. Para quién

| Perfil | Qué necesita sobre todo | Hoy |
|---|---|---|
| Atletas de resistencia | Plan hacia una carrera, ajuste diario, envío al reloj | Hecho en buena parte (modo carrera) |
| Apasionados del fitness | Plan de fuerza y cardio sin fecha, progreso visible | Hecho en parte (Salud) |
| Buscadores de longevidad | Tendencias de sueño, HRV, FC en reposo, VO2max, peso; mínimos de la OMS. Solo lo que miden relojes y básculas: sin analíticas ni marcadores clínicos (decisión del 2026-10-22) | Falta casi todo (no hay historial) |
| Entrenadores y preparadores | Ver y planificar a varios atletas | Más adelante (Fase 4, decisión del 2026-10-22) |

Los tres primeros son la misma persona en momentos distintos: alguien prepara una carrera, la termina y sigue entrenando
por salud. La app no cambia de modo, cambia de objetivo.

## 3. Qué tomamos de cada una

| | Garmin Connect | Apple Health / Google Health | TrainSync |
|---|---|---|---|
| Decide la sesión de hoy | Sí (Garmin Coach, sugerencias diarias) | No | Sí, con el motivo de cada decisión y su evidencia |
| Reúne datos de varias marcas | No (solo Garmin) | Sí | Sí: intervals.icu hoy; Apple Salud y Health Connect con la app móvil |
| Historial y tendencias | Sí | Sí | **Falta** (Fase 2) |
| Explica sus fórmulas | No (Firstbeat es cerrado) | No aplica | Sí: motor determinista y documentado |
| Precio | Gratis + Connect+ de pago | Gratis | Gratis, sin niveles de pago ni anuncios |

## 4. Principios (no negociables)

1. **Un solo sistema.** Un motor, una navegación, un lenguaje visual. Nada de "modo carrera" y "modo salud" separados.
2. **Lenguaje llano primero, detalle al tocar.** Cada número tiene una frase que lo explica ("Forma: descansado"); el
   término técnico (TSB, HRV, RIR) aparece al tocar, nunca solo.
3. **Toda decisión se explica.** El estado de hoy, la sesión y su intensidad salen de la misma decisión del motor y la
   pantalla muestra ese motivo, no una interpretación aparte.
4. **Evidencia etiquetada.** Cada regla con [Verificado], [Práctica] o [Inferencia] y su fuente.
5. **Tus datos son tuyos.** Exportar y borrar en un toque, consentimiento separado para salud (RGPD), y ningún dato de
   usuario pasa por una IA: el motor no usa IA en tiempo de uso.
6. **Gratis y abierto (AGPL-3.0).** Sin niveles de pago, anuncios ni notificaciones comerciales.
7. **No es un producto sanitario.** Mide y orienta; no diagnostica ni trata.

## 5. Estructura de la app

Cuatro apartados y un flujo de objetivo. Todo lo de hoy (Salud, Plan, Hoy) se reparte aquí; nada queda fuera.

### Objetivo (flujo propio, fuera del perfil)

- **Primera vez:** la app empieza preguntando qué quieres (como Garmin): preparar una carrera, ponerte en forma, ganar
  fuerza y músculo o mejorar tu salud a largo plazo. Después, días y minutos disponibles, y si quieres conectar tu reloj.
- **Al terminar una carrera:** la app lo detecta y vuelve a preguntar (¿otra carrera, mantener la forma, descansar?).
- **Siempre se puede abandonar o reajustar** desde Hoy o Calendario, sin pasar por el perfil.
- Un objetivo de carrera no sustituye a la salud: el suelo de la OMS (150 min y fuerza 2 días) se cumple siempre.

### Hoy

- **Tu cuerpo hoy en una frase**, salida de la misma decisión que la sesión ("Has dormido poco: hoy suave").
- **La sesión de hoy** y sus alternativas en tus otros deportes. Elegir una la convierte en la sesión del día (en la app
  y en el reloj).
- Enviar al reloj, marcar como hecha, "hoy no puedo".
- Check-in opcional (sueño, energía, agujetas) solo si no hay reloj que lo mida, y diciendo para qué sirve.

### Calendario

- **Vista mensual** (y semanal al tocar una semana). El pasado muestra lo que hiciste de verdad; el futuro, el plan.
- Cada actividad hecha aparece emparejada con la sesión planificada y **con un título que dice lo que fue** ("Rodaje Z3",
  "Tempo"), no el nombre automático del ciclocomputador; el título se corrige también en intervals.icu.
- Tocar un día: detalle, mover la sesión, cambiar el tiempo disponible.

### Progreso

- Tendencias con tu rango normal: sueño, HRV, FC en reposo, peso, pasos, VO2max, ritmo o potencia umbral y carga
  ("Forma", "Forma física" y "Fatiga" con su explicación, no "TSB/CTL/ATL" a secas).
- Minutos activos y días de fuerza frente a la recomendación de la OMS, semana a semana.
- Hitos: mejores marcas, semanas cumplidas.

### Perfil

- Tus datos, deportes, días y minutos, material (gimnasio o peso corporal), conexiones (reloj, intervals.icu), privacidad
  (exportar, borrar). **Sin objetivo.**

### Entrenadores (más adelante)

- Un espacio aparte para ver y planificar a varios atletas con su permiso. Exige permisos por atleta y probablemente la
  autorización OAuth de intervals.icu en vez de la clave de cada usuario [Inferencia: sin comprobar].

## 6. Un solo motor

- El objetivo decide la periodización: **sin fecha** (bloques de 4 semanas que se renuevan, lo que hoy es Salud) o
  **hacia un evento** (fases base → construcción → pico → taper, lo que hoy es el modo carrera).
- Los mismos datos de entrada para los dos: perfil, disponibilidad, lo hecho de verdad y el estado del día.
- Se reutiliza el código que hay: `training_engine.py` (periodización y catálogo de sesiones) y `health_plan.py`
  (dosis OMS, fuerza, semanas). El trabajo es unir sus salidas en un único plan por usuario, no reescribirlos.

## 7. Arquitectura: todo en el móvil (decidido el 2026-10-22)

- **Apps nativas:** SwiftUI en iOS y Jetpack Compose en Android.
- **Motor en un núcleo compartido** por las dos apps, que corre en el propio móvil. Propuesta: Kotlin Multiplatform
  [sin decidir]. El motor actual en Python se reescribe una vez; la validación en Python (`backend/validation/`) sirve de
  referencia: el núcleo nuevo debe dar los mismos planes con las mismas entradas.
- **Los datos se quedan en el móvil:** Apple Salud / Health Connect se leen en el dispositivo e intervals.icu se llama
  directamente desde la app con la clave del usuario, guardada en el llavero del sistema.
- **Sin servidor ni cuentas** en las fases 1–3: no hay contraseñas, emails ni base de datos con datos de salud de nadie.
  Exportar y borrar siguen existiendo, en el propio móvil.
- **El servidor solo vuelve si hace falta:** entrenadores (compartir datos entre personas), Garmin directo (envía los
  datos a un servidor) y quizá Strava (su autorización) [Inferencia: sin comprobar].
- **La web actual** (Next.js + FastAPI) queda como prototipo de referencia mientras llegan las apps.

**Coste:** sin servidor, lo fijo son las cuentas de desarrollador (Apple ~99 $/año, Google ~25 $ una vez; cifras de
memoria, sin comprobar) y un dominio.

## 8. Datos

| Fuente | Qué aporta | Estado |
|---|---|---|
| intervals.icu | Actividades, carga, umbrales y wellness: sueño (duración, puntuación, calidad), HRV, FC en reposo, pasos, peso, grasa corporal, VO2max, SpO2, tensión arterial, glucosa, fase menstrual, nutrición | Conectado. Campos comprobados en una cuenta real el 2026-10-22 (no todos tienen datos) |
| Manual en la app | Sesiones hechas, check-in del día | Hecho |
| Apple Salud / Health Connect | Lo mismo desde el móvil, sin intervals.icu | Exige app móvil nativa (Fase 3) |
| Strava, Garmin directos | Actividades sin pasar por intervals.icu | Fase 5; Garmin exige aprobación de su programa de desarrolladores [Práctica: sin comprobar] |

## 9. Hoja de ruta

| Fase | Qué | Resultado |
|---|---|---|
| 1. Cohesión | Estructura de la sección 5, un solo motor, flujo de objetivo, lenguaje llano, errores de la revisión | Una app que se entiende de punta a punta |
| 2. Progreso | Historial y tendencias con los datos de intervals.icu | Lo básico de Garmin Connect y Apple Health |
| 3. Apps nativas | Núcleo compartido, iOS y Android, Apple Salud y Health Connect, notificaciones | Usable sin intervals.icu y sin servidor |
| 4. Entrenadores | Varios atletas por cuenta | Abre el perfil profesional |
| 5. Integraciones | Strava y Garmin directos (con servidor) | Plataforma completa |

## 10. La revisión del 2026-10-22 en esta estructura

| Punto | Dónde | Fase |
|---|---|---|
| 1. "Recuperación" arriba y "Aeróbico base" abajo | Hoy: la frase sale de la decisión del motor | 1 |
| 2. Títulos de las actividades | Calendario (y en intervals.icu) | 1 |
| 3. La alternativa elegida pasa a ser la principal | Hoy | 1 |
| 4. Calendario mensual con lo hecho | Calendario | 1 |
| 5. Forma, fitness y HRV sin explicar | Todos (principio 2) | 1 |
| 6. Para qué sirve Salud | Desaparece: se reparte en Hoy, Calendario y Progreso | 1 |
| 7. Objetivo fuera del perfil, como Garmin | Flujo de objetivo | 1 |
| 8. Historial de sueño, HRV, ritmo, VO2max | Progreso | 2 |

## 11. Decisiones

| Pregunta | Decisión (2026-10-22) |
|---|---|
| Móvil | Apps nativas, todo en el móvil (sección 7) |
| Longevidad | Solo lo que miden relojes y básculas; sin analíticas |
| Entrenadores | Más adelante (Fase 4) |
| Financiación | Sin servidor: solo cuentas de desarrollador y dominio |
| Nombre | Abierto. Candidato: **Biotos** (sección 12) |

## 12. Nombre

Comprobado el 2026-10-22 en App Store España (API de búsqueda de Apple), dominios (RDAP) y búsqueda web. **Sin comprobar
en registros de marcas** (EUIPO, OEPM): hacerlo antes de usar ningún nombre.

| Nombre | Resultado |
|---|---|
| Bios | Descartado: BIOS Health (Cambridge, salud) usa bios.health; bios.app y biosapp.com registrados |
| PhysOS | Arriesgado: ya hay una app "PHYSOS" (Medicina) en App Store |
| Anabios | Arriesgado: AnaBios Corp. (San Diego, investigación farmacéutica) |
| **Biotos** | Sin apps ni empresas con ese nombre encontradas; biotos.app y biotos.health libres (biotos.com registrado). Del griego *bíotos*, "vida, sustento", y termina en OS |
