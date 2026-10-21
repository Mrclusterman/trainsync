# TrainSync: un motor determinista y explicable de planificación del ejercicio para la salud y el rendimiento

**Memoria del proyecto**
**Autor:** Gabriel Miralles Álvarez
**Fecha:** 21 de octubre de 2026
**Código y documentación:** repositorio `trainsync` (licencia AGPL-3.0-only)

> TrainSync es un proyecto de aplicación, no un trabajo académico para presentar. Esta memoria sigue la estructura y las
> exigencias de un trabajo académico (estado del arte con fuentes verificadas, diseño trazable, validación reproducible y límites
> declarados) para que las decisiones del producto sean revisables con el mismo rigor.

---

## Resumen

Las aplicaciones de planificación del entrenamiento más extendidas son de pago o reservan sus funciones útiles a una
suscripción, y casi todas están pensadas para preparar una competición: una vez superada, el usuario pierde el motivo para
seguir usándolas. Este trabajo presenta TrainSync, una aplicación gratuita y de código abierto cuyo núcleo es un motor de
planificación **determinista y explicable**, sin inteligencia artificial en tiempo de uso, que genera planes semanales para
cuatro objetivos: preparar un evento, ponerse en forma, ganar fuerza y músculo y perder grasa. Los tres últimos no tienen
fecha de fin: se organizan en bloques de cuatro semanas que se renuevan según la adherencia real del usuario.

El modelo parte de un suelo común derivado de las recomendaciones de la Organización Mundial de la Salud (OMS, 2020) —150
minutos semanales de actividad aeróbica moderada equivalente y fuerza dos o más días— y de la posición del American College
of Sports Medicine sobre entrenamiento de fuerza (ACSM, 2026), y cada regla se etiqueta con el grado de evidencia que la
respalda. Se implementó un backend en Python (FastAPI) con cuentas de usuario, consentimiento explícito para datos de salud
y cifrado de credenciales, una aplicación web (Next.js) y la integración con intervals.icu para enviar las sesiones al
reloj como entrenamientos estructurados.

La validación combina 175 pruebas automáticas, una simulación de seis perfiles con tres niveles de adherencia durante 24
semanas frente a once criterios derivados de la evidencia, y un caso real. Cumplen 109 de las 110 comprobaciones
aplicables; la simulación descubrió seis defectos del motor que los tests unitarios no detectaban y que se corrigieron. La
comprobación que no se cumple es un conflicto estructural documentado entre el suelo de la OMS y la variedad de la carga en
un caso límite. La eficacia en personas queda como trabajo futuro.

**Palabras clave:** planificación del entrenamiento, prescripción del ejercicio, salud, sistemas deterministas,
explicabilidad, aplicaciones de salud, adherencia.

## Abstract

Mainstream training-planning apps are paid or paywall their useful features, and most target a single competition, after
which users lose their reason to keep using them. This work presents TrainSync, a free, open-source application built
around a **deterministic, explainable** planning engine, with no artificial intelligence at run time, that produces weekly
plans for four goals: an event, general fitness, strength and muscle, and fat loss. The last three have no end date: they
run in renewable four-week blocks adjusted to the user's actual adherence. The model enforces a common floor derived from
the World Health Organization 2020 guidelines (150 minutes of moderate-equivalent aerobic activity and muscle strengthening
on two or more days per week) and the 2026 ACSM resistance-training position stand, and every rule is tagged with its level
of evidence. The system comprises a Python/FastAPI backend with user accounts, explicit consent for health data and
encrypted credentials, a Next.js web app, and an intervals.icu integration that sends sessions to the user's watch as
structured workouts. Validation combines 175 automated tests, a 24-week simulation of six profiles at three adherence levels
against eleven evidence-based criteria, and a real-account case: 109 of 110 applicable checks pass. The simulation exposed
six engine defects that unit tests had missed, all fixed; the remaining failure is a documented structural conflict between
the WHO floor and load variety in an edge case. Efficacy in people is left as future work.

**Keywords:** training planning, exercise prescription, health, deterministic systems, explainability, health apps,
adherence.

---

## Índice

1. Introducción
2. Objetivos
3. Estado del arte
4. Metodología
5. Diseño del modelo
6. Implementación
7. Validación y resultados
8. Discusión
9. Conclusiones
10. Trabajo futuro
11. Bibliografía
12. Anexos

---

## 1. Introducción

### 1.1 Motivación

La actividad física regular es uno de los factores de salud con mejor relación entre beneficio y coste, y la OMS recomienda
a todos los adultos un mínimo semanal de actividad aeróbica y de fortalecimiento muscular (Bull et al., 2020). Sin embargo,
la planificación estructurada del entrenamiento —qué hacer cada día, con qué intensidad y cómo progresar— está dominada por
aplicaciones comerciales cuyo modelo de negocio choca con la salud de sus usuarios en dos puntos:

1. **Coste y muros de pago.** El análisis de 1.734 reseñas recientes de TrainingPeaks y Garmin Connect realizado en este
   trabajo (§3.6) muestra que la queja más repetida es pagar por funciones básicas: la suscripción Connect+ que Garmin
   lanzó en 2025 (53 reseñas) y una versión gratuita de TrainingPeaks que el propio usuario describe como un anuncio
   permanente de la versión premium.
2. **Planes con fecha de caducidad.** Las herramientas de planificación giran en torno a una competición. Al terminarla
   desaparece la estructura y, con ella, el motivo para seguir. Es un problema serio en salud, donde la mitad de quienes
   empiezan un programa de ejercicio lo abandonan en pocos meses (§3.5).

Además, los algoritmos comerciales son cerrados: el usuario recibe una sesión sin saber por qué, ni con qué evidencia. Y la
alternativa de moda —pedir el plan a un modelo de lenguaje— es no determinista, difícil de auditar y, en algunas
plataformas, incompatible con sus condiciones de uso de datos (Strava prohíbe usar los datos de su API en modelos de IA).

### 1.2 Propuesta

TrainSync propone un motor de planificación **determinista** (mismas entradas, mismo plan), **explicable** (cada decisión
lleva su motivo visible y cada regla su etiqueta de evidencia) y **gratuito y abierto** (AGPL-3.0), que sirva de ayuda
diaria y no solo de preparación para una carrera. La inteligencia artificial se usó como herramienta de desarrollo (§4.4),
pero no forma parte del producto.

### 1.3 Estructura de la memoria

El capítulo 2 fija los objetivos. El 3 revisa la evidencia sobre prescripción del ejercicio, el estado de las integraciones
con relojes y la competencia. El 4 describe la metodología. Los capítulos 5 y 6 presentan el diseño del modelo y su
implementación. El 7 recoge la validación y sus resultados, y los capítulos 8 a 10, la discusión, las conclusiones y el
trabajo futuro.

## 2. Objetivos

**Objetivo general.** Diseñar, implementar y validar un motor determinista y explicable de planificación del ejercicio,
basado en evidencia, para objetivos de salud sin fecha de fin y para la preparación de eventos, integrado en una aplicación
gratuita.

**Objetivos específicos.**

- OE1. Revisar la evidencia y las guías vigentes sobre actividad física para la salud, entrenamiento de fuerza, entrenamiento
  concurrente y pérdida de grasa, y traducirlas en reglas cuantificables.
- OE2. Diseñar un modelo de planificación por objetivos sin fecha, con progresión por bloques ajustada a la adherencia real.
- OE3. Implementar el motor y una aplicación usable sin reloj, con cuentas, privacidad conforme al RGPD y envío de sesiones
  estructuradas al reloj.
- OE4. Definir criterios de validación medibles y comprobar con simulación que los planes los cumplen.
- OE5. Identificar y documentar los límites del modelo y de su validación.

## 3. Estado del arte

La revisión completa, con cada afirmación etiquetada según su grado de verificación, está en `docs/proyecto/ESTADO_DEL_ARTE.md`.
Se resume aquí lo que condiciona el diseño.

### 3.1 Recomendaciones de actividad física

Las guías de la OMS (Bull et al., 2020) recomiendan a los adultos 150–300 minutos semanales de actividad aeróbica moderada,
o 75–150 de vigorosa, o una combinación equivalente, y actividades de fortalecimiento muscular de intensidad moderada o mayor
que impliquen los grandes grupos musculares dos o más días por semana. A los mayores de 65 años se les añade actividad
multicomponente con énfasis en el equilibrio y la fuerza tres o más días. Las guías subrayan que algo de actividad es mejor
que nada y que conviene empezar con poco y aumentar gradualmente.

### 3.2 Entrenamiento de fuerza

La nueva posición del ACSM (2026), una revisión de revisiones que actualiza la de 2009, concluye que la constancia importa más
que la complejidad: lo esencial es entrenar todos los grandes grupos musculares al menos dos veces por semana. Para fuerza
propone cargas altas (≈ 80 % de 1RM) en 2–3 series por ejercicio, y para hipertrofia, alrededor de 10 series por grupo
muscular y semana; ni llegar al fallo, ni el tipo de material, ni la periodización compleja cambiaron de forma consistente
los resultados del adulto medio. La relación dosis-respuesta entre series semanales e hipertrofia está bien establecida
(Schoenfeld et al., 2017). Para regular la carga sin conocer el 1RM, la escala de esfuerzo basada en repeticiones en reserva
(RIR) es una herramienta validada (Zourdos et al., 2016; Helms et al., 2016). La progresión doble —subir la carga un 2–10 %
al superar el rango de repeticiones— procede del ACSM (2009).

### 3.3 Entrenamiento concurrente y pérdida de grasa

Combinar fuerza y resistencia no compromete la hipertrofia ni la fuerza máxima frente a la fuerza sola, aunque sí la fuerza
explosiva, sobre todo con ambas en la misma sesión (Schumann et al., 2022); la interferencia es mayor con carrera que con
bicicleta (Lundberg et al., 2022). Para perder grasa, 150–250 minutos semanales de actividad moderada dan una pérdida modesta
y más de 250 se asocian a una pérdida clínicamente significativa (Donnelly et al., 2009). El ejercicio aeróbico reduce más la
masa grasa y la fuerza es necesaria para conservar o ganar masa magra (Willis et al., 2012).

### 3.4 Cribado previo

El PAR-Q+ es el cuestionario de referencia, pero su licencia prohíbe incorporarlo a herramientas electrónicas sin permiso
escrito de la PAR-Q+ Collaboration. El algoritmo del ACSM (Riebe et al., 2015) es un procedimiento de decisión publicado que
sí puede implementarse con preguntas propias. Finalmente se optó por un aviso legal y de salud (§5.8).

### 3.5 Adherencia

En torno a la mitad de quienes inician un programa de ejercicio lo abandonan en 3–6 meses (Dishman, citado por los CDC), y en
ensayos estructurados dos tercios de los abandonos ocurren durante la subida de volumen. La consecuencia de diseño es doble:
progresión inicial suave y planes que no terminen.

### 3.6 Competencia y análisis de reseñas

Se analizaron 298 reseñas de TrainingPeaks y 1.436 de Garmin Connect de los dos últimos años (feed RSS oficial del App Store
de EE. UU., Reino Unido y España, y menciones en Hacker News), ordenadas por temas con la herramienta `replica-entrepreneur`
y con todas las citas verificadas como literales (`replica/fixes.md`). Los temas principales fueron los muros de pago y
suscripciones, la sincronización con el reloj, la imposibilidad de planificar desde el móvil y unas métricas en las que el
usuario no confía. Garmin documenta las entradas de sus sugerencias diarias —carga reciente y su reparto entre aeróbico bajo,
alto y anaeróbico, tiempo de recuperación, sueño, sesiones recientes y fecha de la carrera—, pero no sus fórmulas.

### 3.7 Integraciones con relojes (octubre de 2026)

El acceso de terceros a los datos de los relojes es heterogéneo e inestable: Garmin pausó en septiembre de 2026 las nuevas
altas en su programa de desarrolladores; la API web de Fitbit se apaga el 30 de octubre de 2026; Polar ofrece una API abierta;
Apple Salud y Health Connect solo son accesibles desde una aplicación móvil; y Strava permite su API para motores no basados
en IA. intervals.icu actúa como agregador con una clave personal.

## 4. Metodología

### 4.1 Fases

El trabajo siguió cinco fases secuenciales con entregables propios (`docs/proyecto/PLAN.md`): A, estado del arte; B, diseño del
modelo; C, implementación; D, validación; E, esta memoria. Antes, el motor de preparación de eventos se había desarrollado y
contrastado con la bibliografía (`docs/MOTOR_ENTRENAMIENTO.md`), y la fase A se apoyó en él.

### 4.2 Etiquetado de la evidencia

Cada regla del motor lleva una etiqueta: **[Verificado]**, si se comprobó en la fuente; **[Práctica]**, si es práctica
establecida documentada por entrenadores o fabricantes; **[Inferencia]**, si es una decisión del autor sin respaldo
publicado. Las inferencias son precisamente lo que la validación debe poner a prueba.

### 4.3 Herramientas

Python 3.14 y FastAPI para el backend; SQLAlchemy con SQLite (PostgreSQL en producción); Next.js 16, React 19 y Tailwind CSS 4
para la web; pytest para las pruebas; Playwright para las pruebas en navegador; Git y GitHub para el control de versiones.

### 4.4 Uso de inteligencia artificial

El desarrollo se apoyó en un asistente de IA (Claude, de Anthropic, a través de Claude Code) para buscar y contrastar fuentes,
escribir código y pruebas, y redactar documentación, siempre bajo la dirección del autor, que fijó los objetivos, el alcance y
las decisiones de producto. Tres garantías acotan su papel: (1) el producto no usa IA en tiempo de uso: el motor es
determinista y sus reglas están escritas y citadas; (2) ningún dato de usuario se envió a la IA: las pruebas con
integraciones usaron datos simulados o datos propios del autor; (3) cada afirmación bibliográfica se contrastó con la fuente
y se etiquetó con su grado de verificación, señalando explícitamente lo que no pudo verificarse.

## 5. Diseño del modelo

La especificación completa está en `docs/proyecto/DISENO.md` (objetivos sin fecha) y `docs/MOTOR_ENTRENAMIENTO.md` (eventos).

### 5.1 Principios

1. Determinista y explicable. 2. Suelo de salud común: todo plan cumple las guías de la OMS, y el objetivo solo decide cuánto
se sube por encima y cómo se reparte. 3. Constancia antes que complejidad. 4. Sin fecha de fin. 5. Funciona con pocos datos:
con reloj usa FC, HRV y sueño; sin reloj, sensación y lo que el usuario registra.

### 5.2 Entradas y dosis semanal

El usuario indica su objetivo, los días y minutos disponibles, si hace fuerza en gimnasio, con su peso o no la hace, las
actividades aeróbicas que le gustan (caminar, correr, bici, nadar), su nivel y su edad. La dosis se mide en **minutos
moderados equivalentes**, como la OMS: los minutos en Z2–Z3 cuentan 1, en Z4 o más cuentan 2 y en Z1, actividad ligera, no
cuentan.

| Objetivo | Aeróbico semanal (meta) | Días de fuerza | Series por grupo muscular y semana |
|---|---|---|---|
| Suelo (todos) | ≥ 150 min | ≥ 2 | ≥ 4 |
| Ponerse en forma | 150 → 300 | 2–3 | 6 → 10 |
| Fuerza y músculo | 150 | 3–4 | 10 → 20 |
| Perder grasa | 150 → 250 o más | 2–3 | 6 → 10 |
| Mayores de 65 (se suma) | — | equilibrio y fuerza ≥ 3 | — |

### 5.3 Bloques y progresión

Cada bloque dura cuatro semanas: tres de carga, con una subida del 10 % semanal hasta la meta, y una de descarga (−30 %). El
punto de partida depende del nivel (60, 80 o 100 % de la meta). Al terminar el bloque, la adherencia —sesiones hechas sobre
planificadas, reconstruidas a partir del plan determinista de cada semana— decide el siguiente: sube con 85 % o más, se
mantiene entre 60 y 85 %, y baja un 20 % por debajo de 60 %, sin bajar nunca de 60 minutos. Es la respuesta del modelo a la
evidencia sobre abandono: el plan se adapta a lo que el usuario hace de verdad.

### 5.4 Reparto semanal

Algoritmo determinista sobre los días disponibles: al menos un día de descanso; días de fuerza lo más separados posible
(cuerpo completo con tres días o menos, torso y pierna con cuatro); resistencia en los días restantes y, si no basta, en los
de fuerza; en fuerza y músculo, nada de carrera el día de pierna si hay otra opción (Schumann et al., 2022; Lundberg et al.,
2022). El suelo aeróbico de la OMS tiene prioridad sobre seguir subiendo series de fuerza: si no se alcanza, la sesión de
fuerza ocupa una parte menor del día y el reparto se repite. Si la dosis no cabe en el tiempo disponible, el plan lo dice en
lugar de alargar las sesiones.

### 5.5 Sesiones

**Fuerza.** Patrones de movimiento (sentadilla, bisagra de cadera, empujes, tracciones, zancada, core) con versión de
gimnasio y de peso corporal; carga regulada por RIR; progresión doble; ondulación diaria con tres días (pesado, ligero,
medio) para variar la carga sin cambiar el total; el core, por tiempo. Entre las semanas posibles, el motor elige la primera que cumple el
suelo de la OMS con monotonía de Foster menor que 2, cediendo antes tiempo de fuerza que días y, si hace falta, convirtiendo
un día libre en descanso.
**Resistencia.** Un catálogo de 67 sesiones para carrera, ciclismo, natación y caminar, con intensidad polarizada para
usuarios con experiencia y solo moderada para principiantes.

### 5.6 Ajuste diario

Con datos de recuperación se aplican las reglas del motor de eventos (HRV con media de 7 días frente a su variación normal,
sueño, forma). Sin reloj, tres preguntas (sueño, energía y agujetas, de 1 a 5) convierten el día en versión suave o en
descanso activo.

### 5.7 Objetivo "evento" y comparación con Garmin

El motor de eventos (periodización con taper por distancia y prioridad A/B/C, plantilla polarizada, ajuste diario por forma,
HRV y sueño) incorpora además el método documentado de Garmin con fórmulas propias: la sesión de calidad del día cubre el
tipo de carga que falta en los últimos 28 días, se añadieron sesiones tempo y anaeróbicas, y los ritmos de carrera se
calculan desde las zonas de frecuencia cardíaca con la relación FC-velocidad del propio usuario, obtenida de sus carreras en
llano (`MOTOR_ENTRENAMIENTO.md` §12–15).

### 5.8 Seguridad, privacidad y datos

Se diseñó un cribado con el algoritmo del ACSM, pero se sustituyó por un **aviso legal y de salud** al crear el perfil, por
decisión del autor: un cuestionario médico no encaja con el público general al que se dirige la aplicación. El riesgo
asumido —sin cuestionario, el plan no limita la intensidad a quien tenga enfermedad o síntomas— se declara en
`docs/CUMPLIMIENTO.md`; los principiantes siguen limitados a intensidad moderada por su nivel. En privacidad se aplicaron el
consentimiento explícito y separado para datos de salud (RGPD art. 9.2.a), la edad mínima de 14 años (LOPDGDD art. 7), la
minimización (año de nacimiento en vez de fecha), la exportación (art. 20) y el borrado inmediato de la cuenta (art. 17).
Todas las fuentes de datos se normalizan a dos estructuras, actividad y bienestar, de modo que el motor no depende de la marca
del reloj.

## 6. Implementación

### 6.1 Arquitectura

Backend FastAPI con 17 endpoints, motor en módulos puros (`training_engine.py`, `health_plan.py`, `screening.py`,
`workout_text.py`), persistencia con SQLAlchemy y una web Next.js que llama al backend solo desde el servidor (Server
Components y Server Actions): el navegador nunca ve la URL del backend ni el token, que viaja en una cookie `httpOnly`.

| Componente | Tamaño aproximado |
|---|---|
| Backend (sin tests) | 2.340 líneas de Python |
| Tests | 860 líneas, 175 pruebas |
| Web | 1.250 líneas de TypeScript/TSX |

### 6.2 Seguridad

Contraseñas con scrypt de la biblioteca estándar, con los parámetros mínimos de OWASP; sesiones con JWT; claves de
intervals.icu de cada usuario cifradas con AES-256-GCM; secretos fuera del repositorio.

### 6.3 Envío al reloj

Las sesiones se traducen al formato de entrenamiento de intervals.icu y se suben a su calendario, que las sincroniza con el
reloj. Todos los formatos se comprobaron contra la API real, lo que destapó tres comportamientos no documentados que el
diseño tuvo que sortear: intervals.icu ignora el identificador propio del evento (se usa `external_id`), descarta el texto de
esfuerzo percibido escrito después de la duración, y no acepta pulsaciones absolutas, por lo que los objetivos de FC se
expresan en porcentaje de la FC máxima. Según usuarios de su foro, al reloj solo llega una métrica por sesión, de modo que
cada sesión usa una sola (ritmo, FC o potencia).

## 7. Validación y resultados

El método y los resultados completos están en `docs/proyecto/VALIDACION.md` y se reproducen con `python -m validation.run`.

### 7.1 Método

1. **Verificación:** 175 pruebas automáticas.
2. **Simulación:** 6 perfiles (principiante sedentario, persona de 70 años, gimnasio, corredor avanzado, pérdida de grasa y
   pérdida de grasa con tope de intensidad) × 3 adherencias (95, 75 y 40 %) × 24 semanas, frente a once criterios.
3. **Caso real:** el motor con la fisiología de la cuenta de intervals.icu del autor.

### 7.2 Resultados

**109 de 110 comprobaciones aplicables cumplen**; 10 no aplican, bien porque con adherencia media o baja la progresión aún no
ha alcanzado la meta, bien porque el perfil no tiene tiempo físico para 150 minutos (estimación independiente del motor).

| Criterio | Resultado |
|---|---|
| V1 Suelo de la OMS en semanas estables | Cumple en todos los casos aplicables |
| V2 Mayores de 65: equilibrio y fuerza ≥ 3 días | Cumple |
| V3 Fuerza: cada patrón ≥ 2 veces por semana | Cumple |
| V4 Pérdida de grasa: ≥ 250 min o aviso | Cumple |
| V5 Subida ≤ 10 % semanal y descarga cada 4.ª semana | Cumple |
| V6 Tope de intensidad | Cumple |
| V7 Descanso semanal; pierna nunca en días seguidos | Cumple |
| V8 Sin carrera el día de pierna (fuerza y músculo) | Cumple |
| V9 Monotonía de Foster < 2 | Cumple en 17 de 18 casos; el restante es un conflicto estructural (§8.2) |
| V10 Determinismo | Cumple |
| V11 Menos volumen con adherencia baja | Cumple |
| Caso real | Cumple: LTHR 174, FC máx. 192 → techo fácil 144 ppm = 75 %; objetivos entre 50 y 75 % |

### 7.3 Defectos encontrados

La primera simulación dio 106 de 120 y destapó seis defectos que los tests unitarios no veían: subidas semanales de hasta
+11,6 % por redondeo; volumen sin mínimo con adherencia baja; sesiones de calidad que no aparecían cuando el deporte no tenía
ese tipo; sesiones de fuerza que, al subir las series, expulsaban la actividad aeróbica (semanas estables de solo 35 minutos
equivalentes); semanas de seis días con la carga diaria demasiado uniforme (monotonía de hasta 2,42); y un ajuste de día suave
que no acortaba. También se corrigieron dos errores del propio protocolo, que dejaban
dos criterios sin comprobar casi nada. Que la validación encontrara defectos reales es el principal argumento de su valor.

## 8. Discusión

### 8.1 Frente a Garmin y la competencia

TrainSync cubre los mismos tipos de sesión que las sugerencias diarias de Garmin y su criterio central (cubrir el tipo de carga
que falta), pero con fórmulas publicadas y citadas, y añade lo que Garmin no ofrece: bici sin potenciómetro, objetivos de
salud sin fecha que se adaptan a la adherencia y gratuidad. La comparación es de diseño, no de resultados: no existe una base
común para medir qué plan funciona mejor en personas.

### 8.2 Monotonía: un conflicto que el motor resuelve con una prioridad declarada

Las semanas de seis días salían con la carga diaria demasiado uniforme. Hacer que el motor elija entre semanas candidatas
—cediendo tiempo de fuerza y, si hace falta, añadiendo un día de descanso— lo resolvió en 17 de 18 casos sin perder el suelo
de la OMS. En el caso restante (tope de intensidad moderada y adherencia baja, con la dosis pegada al suelo) se comprobó que
ninguna semana posible cumple ambas cosas, y el motor prioriza el suelo: el umbral de 2 viene del seguimiento de deportistas
con cargas altas (Foster, 1998) y este caso es de carga baja. El conflicto y la prioridad quedan declarados.

### 8.3 Decisiones con coste

- **Aviso legal en vez de cuestionario:** mejor experiencia para el público general, a cambio de no limitar la intensidad en
  personas con enfermedad o síntomas.
- **Inferencias:** el punto de partida (60/80/100 %), la progresión del 10 %, los umbrales de adherencia y otros parámetros
  no tienen respaldo publicado. La validación comprueba que el resultado cumple las guías, no que esos valores sean óptimos.
- **Dependencia de terceros:** las integraciones dependen de programas de desarrolladores cambiantes (§3.7).

### 8.4 Limitaciones de la validación

Perfiles sintéticos y adherencia constante; minutos equivalentes calculados por zonas de FC que, sin reloj, dependen de que el
usuario siga la prueba del habla; y, sobre todo, ausencia de validación de eficacia en personas.

## 9. Conclusiones

Se ha construido un motor de planificación determinista y explicable que traduce las guías de la OMS y del ACSM a reglas
cuantificables, para objetivos de salud sin fecha y para eventos, integrado en una aplicación gratuita y conforme a los
requisitos de privacidad del RGPD (OE1–OE3). Se definieron once criterios medibles derivados de la evidencia y una validación
reproducible que los comprueba (OE4), y cuyo resultado —109 de 110 comprobaciones aplicables, seis defectos encontrados y
corregidos, y un conflicto estructural explicado— muestra que la validación por simulación detecta errores que las pruebas
unitarias no ven. Los límites del modelo y de su validación quedan documentados (OE5).

La aportación principal no es un algoritmo nuevo, sino un método: cada regla citada y etiquetada, cada decisión visible para
el usuario y cada afirmación de la memoria reproducible desde el repositorio.

## 10. Trabajo futuro

1. **Validación de eficacia:** estudio con participantes, aprobado por un comité de ética, que mida adherencia y cambios en
   la condición física frente a un grupo de control.
2. **Aplicación móvil** con Apple Salud y Health Connect, e integración directa con Polar y Strava.
3. **Calibración de las inferencias** con datos de uso reales (punto de partida, progresión, umbrales de adherencia).
4. **Migraciones de base de datos** y despliegue con PostgreSQL antes de abrir la aplicación al público.

## 11. Bibliografía

- American College of Sports Medicine. (2009). Progression models in resistance training for healthy adults. *Medicine &
  Science in Sports & Exercise, 41*(3), 687–708.
- American College of Sports Medicine. (2026). Resistance training prescription for muscle function, hypertrophy, and
  physical performance in healthy adults: An overview of reviews. *Medicine & Science in Sports & Exercise, 58*(4), 851–872.
  [Leído a través de la nota de prensa del ACSM; texto completo no consultado.]
- Bull, F. C., Al-Ansari, S. S., Biddle, S., et al. (2020). World Health Organization 2020 guidelines on physical activity and
  sedentary behaviour. *British Journal of Sports Medicine, 54*(24), 1451–1462. https://doi.org/10.1136/bjsports-2020-102955
- Donnelly, J. E., Blair, S. N., Jakicic, J. M., et al. (2009). Appropriate physical activity intervention strategies for
  weight loss and prevention of weight regain for adults. *Medicine & Science in Sports & Exercise, 41*(2), 459–471.
- Foster, C. (1998). Monitoring training in athletes with reference to overtraining syndrome. *Medicine & Science in Sports &
  Exercise, 30*(7), 1164–1168. [Referencia citada en `MOTOR_ENTRENAMIENTO.md`; volumen y páginas no verificados de nuevo.]
- Helms, E. R., Cronin, J., Storey, A., & Zourdos, M. C. (2016). Application of the repetitions in reserve-based rating of
  perceived exertion scale for resistance training. *Strength and Conditioning Journal, 38*(4), 42–49.
- Lundberg, T. R., et al. (2022). The effects of concurrent aerobic and strength
  training on muscle fiber hypertrophy: A systematic review and meta-analysis. *Sports Medicine, 52*(10), 2391–2403.
  https://doi.org/10.1007/s40279-022-01688-x
- Riebe, D., Franklin, B. A., Thompson, P. D., et al. (2015). Updating ACSM's recommendations for exercise preparticipation
  health screening. *Medicine & Science in Sports & Exercise, 47*(11), 2473–2479. https://doi.org/10.1249/MSS.0000000000000664
- Schoenfeld, B. J., Ogborn, D., & Krieger, J. W. (2017). Dose-response relationship between weekly resistance training volume
  and increases in muscle mass: A systematic review and meta-analysis. *Journal of Sports Sciences, 35*(11), 1073–1082.
- Schumann, M., Feuerbacher, J. F., Sünkeler, M., et al. (2022). Compatibility of concurrent aerobic and strength training for
  skeletal muscle size and function: An updated systematic review and meta-analysis. *Sports Medicine, 52*, 601–612.
- Warburton, D. E. R., Jamnik, V., Bredin, S. S. D., et al. (2013). The PAR-Q+ and ePARmed-X+. *Canadian Family Physician*.
- Willis, L. H., Slentz, C. A., Bateman, L. A., et al. (2012). Effects of aerobic and/or resistance training on body mass and
  fat mass in overweight or obese adults. *Journal of Applied Physiology, 113*(12), 1831–1837.
  https://pubmed.ncbi.nlm.nih.gov/23019316/
- Zourdos, M. C., Klemp, A., Dolan, C., et al. (2016). Novel resistance training-specific rating of perceived exertion scale
  measuring repetitions in reserve. *Journal of Strength and Conditioning Research, 30*(1), 267–275.
- Referencias del motor de eventos (Friel, Bosquet, Plews, Daniels y otras): `docs/MOTOR_ENTRENAMIENTO.md` §16.
- Fuentes técnicas sobre integraciones (Garmin, Google, Polar, Samsung, Strava, intervals.icu): `docs/proyecto/ESTADO_DEL_ARTE.md` §8.

## 12. Anexos

- **A. Reproducibilidad.** `cd backend && pip install -e '.[dev]' && pytest` (175 pruebas) y `python -m validation.run`
  (simulación; añade `--real` con las credenciales de intervals.icu en el entorno). Resultados en
  `docs/proyecto/validacion/resultados.md` y `resultados.csv`.
- **B. Especificaciones.** `docs/proyecto/DISENO.md` (objetivos sin fecha, criterios V1–V11), `docs/MOTOR_ENTRENAMIENTO.md`
  (eventos), `docs/MEMORIA_TECNICA.md` (requisitos y arquitectura).
- **C. API.** Documentación OpenAPI generada por FastAPI en `/docs`; tabla de endpoints en el `README.md`.
- **D. Cumplimiento normativo.** `docs/CUMPLIMIENTO.md`.
- **E. Análisis de reseñas.** `replica/fixes.md`, `replica/feedback-tp.md`, `replica/feedback-gc.md`.
