# Validación del motor — TrainSync

> **Fecha:** 2026-10-29 · Reproducible: `VALIDATION_REPORT=1 ./gradlew :shared:jvmTest --tests '*ValidationTest*'`.
> Sin la variable solo comprueba; se ejecuta en cada compilación de la nube. Resultados completos:
> `validacion/resultados.md`. Hasta el 2026-10-21 la validación estaba en Python (`prototype/backend/validation/`); el
> 2026-10-29 se pasó al núcleo de Kotlin, que es la única fuente del motor, y se amplió a las carreras y al ajuste del día.

## 1. Método

Se distinguen tres niveles, de menor a mayor alcance:

1. **Verificación** (¿el código hace lo que dice el diseño?): 194 tests automáticos (`pytest`) sobre las reglas del
   diseño, el cribado del ACSM (sus ocho ramas), la API, las cuentas y los derechos RGPD.
2. **Validación por simulación** (¿los planes cumplen la evidencia?), con datos sintéticos, en tres partes:
   - **Planes sin fecha (V1–V12):** 7 perfiles × 3 niveles de adherencia (95 %, 75 % y 40 % de las sesiones hechas)
     durante 24 semanas (6 bloques), contra los criterios de `DISENO.md` §11. Perfiles: principiante sedentario, persona
     de 70 años, gimnasio (fuerza y músculo), corredor avanzado, seis días por semana y seis días con tope de intensidad
     del cribado. Los dos últimos eran de pérdida de grasa hasta que se retiró ese objetivo (2026-10-21); con él se retiró
     el criterio V4.
   - **Hacia una carrera (R1–R10):** 7 carreras (10 km con 3 días, media maratón, maratón, trail de 30 km, gran fondo,
     triatlón olímpico y una 10 km preparatoria de prioridad C), cada una desde el inicio de la preparación (20 semanas)
     hasta el día de la prueba, con los días y minutos de cada perfil.
   - **Ajuste del día (E1–E11):** estados sintéticos del reloj o del móvil (fatiga, variabilidad cardíaca, sueño en
     puntuación o en horas, días sin entrenar, esfuerzo raro), respuestas a las tres preguntas y el plan de 14 días.
   - **Natación (N1–N3):** sesiones en metros con el CSS y por esfuerzo sin él.
   - **Combinaciones generadas (F1–F12):** con una semilla fija, 300 perfiles sin fecha (objetivo, nivel, material,
     deportes, de 1 a 7 días de 20 a 240 min, edades de 15 a 82, tope del cribado, con y sin fisiología) durante 8
     semanas, y 300 carreras de los 6 tipos (distancias, prioridades A/B/C, de 2 a 30 semanas vista, desde cualquier
     punto de la preparación). Reglas que valen para todos: sin errores, cada día cabe, descanso semanal, tope del
     cribado, fases en orden, +10 % semanal como mucho, cada descarga baja y determinismo.
3. **Caso real** (`ValidationTest.realAccount`): los mismos criterios con la fisiología y el estado de hoy de una cuenta
   real de intervals.icu (cuenta de pruebas del autor): un plan sin fecha, los objetivos de FC que llegarían al reloj, una
   media maratón de 20 semanas y el plan de 14 días con el estado de hoy. Se ejecuta en local con
   `set -a; . prototype/.env; set +a; VALIDATION_REPORT=1 ./gradlew :shared:jvmTest --tests '*realAccount*'`; sin
   credenciales se omite. El informe (`validacion/resultados-cuenta-real.md`) solo recoge la LTHR y la FC máx. de la cuenta.

Queda fuera, como trabajo futuro, la **validación de eficacia** (si las personas mejoran su forma física siguiendo los
planes): exige un estudio con participantes y aprobación de un comité de ética.

## 2. Resultados

**216 de 216 comprobaciones aplicables cumplen**; 10 no aplican. La prueba admite una lista de fallos conocidos (salen en
el informe, pero no rompen la compilación, y avisa en cuanto se cumplen); hoy está vacía.

### 2.1 Planes sin fecha

| Criterio | Resultado |
|---|---|
| V1 Suelo OMS (≥ 150 min equivalentes y ≥ 2 días de fuerza) en semanas estables | Cumple en todos los casos aplicables. No aplica en 10: sin semanas estables con adherencia media o baja (la progresión aún no ha llegado a la meta), o perfiles en los que es físicamente imposible (el principiante tiene 150 min a la semana en total, fuerza incluida) |
| V2 Mayores de 65: equilibrio y fuerza ≥ 3 días | Cumple |
| V3 Fuerza y músculo: cada patrón ≥ 2 veces por semana; series en la dosis o aviso | Cumple |
| ~~V4 Pérdida de grasa~~ | Retirado con el objetivo |
| V5 Subida semanal ≤ 10 % y descarga cada 4.ª semana | Cumple |
| V6 Tope del cribado: sin Z4+ ni RIR < 3 | Cumple |
| V7 Descanso semanal; pierna pesada nunca en días seguidos | Cumple |
| V8 Fuerza y músculo: sin carrera el día de pierna | Cumple |
| V9 Monotonía de Foster < 2 | Cumple en los 18 casos (máx. 1,99). Hasta el 2026-10-29 fallaba uno, tope de intensidad + adherencia baja (2,26): §4 |
| V10 Determinismo | Cumple |
| V11 Adherencia baja → menos volumen que alta | Cumple |
| V12 Menores de 18 (OMS 2020: 60 min al día de media y fuerza 3 días): la meta sube hacia 420 min, fuerza 3 días y aviso si no cabe | Cumple (perfil de 15 años, añadido el 2026-10-29; antes se le aplicaban las cifras de adulto) |
| Caso real (2026-10-29) | Cumplen las 17 comprobaciones aplicables (V1 no aplica con adherencia media). LTHR 174, FC máx. 192 → techo fácil 144 ppm = 75 % de la FC máx.; objetivos entre el 50 y el 75 %. Media maratón: subida máx. +9,4 %, afinado al 44 %, 93 % del tiempo en Z1–Z2. Umbral estimado con sus 28 sesiones de carrera de 90 días: 166 frente a los 174 configurados (dentro de ±8 ppm, pero por debajo: la media de la sesión incluye el calentamiento). Detalle en `validacion/resultados-cuenta-real.md` |

### 2.2 Hacia una carrera

| Criterio | Resultado |
|---|---|
| R1 Las fases van en orden (base inicial → base → construcción → pico → afinado) | Cumple |
| R2 La carga semanal (TSS) no sube más del 10 % sobre la última semana de carga [Práctica] | Cumple (antes de corregirlo, hasta +27 %: §3.1) |
| R3 Cada semana de descarga tiene menos carga que la anterior | Cumple |
| R4 Afinado de la carrera principal: el volumen baja al 70 % o menos del de las 3 semanas previas (Bosquet 2007: −41 a −60 %) | Cumple |
| R5 Cada día cabe en los minutos que tienes y no hay sesiones en tus días sin entreno | Cumple (antes de corregirlo, 55 días no cabían: §3.1) |
| R6 Al menos un día de descanso por semana | Cumple |
| R7 Distribución polarizada: ≥ 75 % del tiempo de resistencia en Z1–Z2 fuera del afinado (Seiler 2010: ~80 %) | Cumple |
| R8 La tirada larga no pasa del tope por distancia (`LONG_CAP`) | Cumple |
| R9 Nunca dos sesiones de calidad en días seguidos [Práctica] | Cumple |
| R10 Determinismo | Cumple |

### 2.3 Ajuste del día

| Criterio | Resultado |
|---|---|
| E1 Todo normal → la sesión no cambia (sin falsas alarmas) | Cumple |
| E2 Fatiga muy alta (forma < −30) → recuperación | Cumple |
| E3 Variabilidad cardíaca por debajo de lo normal 7 días → sin intensidad | Cumple |
| E4 Mal sueño → sin intensidad y −30 % de carga | Cumple |
| E5 10 días sin entrenar → primero base | Cumple |
| E6 Esfuerzo alto con pulso bajo → sin intensidad | Cumple |
| E7 Respuestas "dormí y estoy mal" → versión suave, sin Z4+ | Cumple |
| E8 Respuestas todo a 1 → solo un paseo | Cumple |
| E9 Móvil, sin puntuación de sueño: 5,2 h cuando sueles dormir 7,4 → sin intensidad y −30 % | Cumple |
| E10 Móvil: 5,4 h cuando sueles dormir 5,6 → sin cambios (se compara con lo normal en ti) | Cumple |
| E11 Plan de 14 días: la 2.ª semana parte del CTL proyectado y no sube más del 10 % (en base, construcción y pico) | Cumple |

### 2.4 Natación

| Criterio | Resultado |
|---|---|
| N1 Con CSS, todas las series en metros y en largos de 25 m | Cumple (64 sesiones) |
| N2 Los metros cuadran con el tiempo al ritmo de la zona | Cumple (174 series) |
| N3 En el reloj, distancia (`mtr`) y ritmo /100 m; sin CSS, por tiempo y esfuerzo | Cumple |

### 2.5 Combinaciones generadas

| Criterio | Resultado |
|---|---|
| F1–F5 Sin fecha: sin errores, cada día cabe, descanso semanal, tope del cribado (sin Z4+; RIR ≥ 3 ligero-moderado, ≥ 2 moderado), determinismo | Cumple en los 300 perfiles |
| F6–F13 Carreras: sin errores, cada día cabe, descanso semanal, fases en orden, +10 % semanal, determinismo, cada descarga baja y, con tope del cribado, ni series ni Z4+ | Cumple en las 300. Una semana sube más del 10 % con el volumen ya al mínimo (las sesiones no bajan de su parte fija): se cuenta aparte en el informe |

## 3. Defectos que encontró la validación (y su corrección)

### 3.1 Carreras (2026-10-29)

La primera ejecución de R1–R10 dio 13 fallos en 6 de las 7 carreras:

| Defecto | Causa | Corrección |
|---|---|---|
| La carga semanal subía hasta +27 % al pasar de base a construcción | El volumen de la fase sube del 100 al 110 % y la plantilla cambia una sesión neuromuscular por una de umbral, todo en la misma semana | `rampScales`: cada semana de la preparación se escala para no pasar del +10 % sobre la última de carga; las de descarga y el afinado no se tocan |
| Días que no cabían en los minutos del usuario (p. ej. 66 de 60 min) | La fuerza solo se quitaba si no quedaban 20 min libres, pero las series tienen una parte fija mayor (26 min o más) | Si la sesión principal y la fuerza no caben juntas, fuera la fuerza, con su motivo |
| Una sesión de series sola no cabía en un día de 45 min (55 min) | Su parte fija (calentamiento y series) no baja con el volumen | Ese día pasa a la versión continua de la sesión, con su motivo |

### 3.2 Combinaciones generadas (2026-10-29)

La primera ejecución de F1–F12 encontró cuatro defectos del motor y dos errores del propio criterio:

| Defecto | Causa | Corrección |
|---|---|---|
| Días cortos (20–30 min) con sesiones de 35 | La sesión no baja de su parte fija y nada lo comprobaba | Si no cabe, la versión corta (recuperación); si tampoco, ese día solo fuerza o descanso, con su motivo |
| Con un solo día a la semana, la fuerza se comía el día (tirada larga de 20 min) | La fuerza solo cedía si no quedaban 20 min | Hacia una carrera, la fuerza solo si deja al menos la mitad del día a la sesión principal |
| Subidas de hasta +18 % con 1–2 días a la semana | El ajuste del 10 % no convergía cuando la fuerza entraba y salía según el volumen | Bisección del volumen entre 0,15 y 1 |
| Semanas de descarga con más carga que la anterior (116 frente a 74) | La descarga coincidía con el paso a construcción (sesiones nuevas) y no se limitaba | La descarga también se limita: como mucho el 80 % de la última semana de carga |
| Con tope del cribado, la carrera seguía con series y tramos en Z4+ | El modo carrera no tenía tope | El cribado del ACSM pasa al núcleo (Screening.kt) y su tope llega también a las carreras: las sesiones de calidad pasan a su versión continua y ningún tramo sube de Z3 (moderado) o Z2 (ligero-moderado); con síntomas y actividad, no se propone ejercicio. Las apps no hacen el cuestionario (decisión del 2026-10-21, `CUMPLIMIENTO.md`): el tope se aplica solo si hay respuestas guardadas |
| *Criterio:* el tope moderado daba fallo con RIR 2 | El motor permite RIR 2 con tope moderado a propósito | El criterio usa el mismo tope que el motor |
| *Criterio:* "fases desordenadas" a más de 20 semanas vista | Antes de la preparación toca base de mantenimiento, que es correcto | Se comprueba desde el inicio de la preparación |

### 3.3 Planes sin fecha (2026-10-21, en Python)

La simulación hizo su función: la primera ejecución dio 106 de 120 y destapó defectos que los tests unitarios no veían.

| Defecto | Causa | Corrección |
|---|---|---|
| Subidas semanales de hasta +11,6 % | El 10 % se aplicaba al valor sin redondear; con minutos pequeños el redondeo lo superaba | Cada semana se calcula sobre la anterior ya redondeada y hacia abajo |
| Con adherencia baja sostenida el volumen bajaba sin límite | La revisión del bloque no tenía mínimo | Mínimo de 60 min (OMS: algo de actividad es mejor que nada) |
| Las sesiones de calidad no aparecían | Si el deporte no tenía ese tipo (caminar no tiene VO2max) o no cabía, se pasaba directamente a aeróbico | Se prueba el otro tipo de calidad antes |
| La fuerza dejaba sin tiempo a la resistencia (35 min equivalentes en semanas estables) | Al subir las series, la sesión de fuerza llenaba el día | El suelo aeróbico de la OMS va antes que seguir subiendo series: la fuerza ocupa una parte menor del día hasta llegar al suelo |
| Monotonía > 2 en los perfiles de 6 días (máx. 2,42) | La semana se llenaba hasta el límite con días casi iguales | Elección entre semanas candidatas: la primera que cumple el suelo de la OMS con monotonía < 2, cediendo antes tiempo de fuerza que días, y si hace falta un día libre más de descanso (DISENO §5.5) |
| El día "suave" no acortaba | La duración se corregía multiplicando y se pasaba hasta el tope; la fuerza ya en 1 serie no podía bajar | Corrección aditiva de la duración; quitar un accesorio si ya hay 1 serie |

También se corrigieron dos errores del propio protocolo de validación: excluir las semanas con aviso dejaba V1 y V9 sin
comprobar casi nada (pasaban "en vacío"). V1 se evalúa ahora contra el suelo de la OMS, y la imposibilidad física se
estima con una fórmula independiente del motor, para no validar el motor consigo mismo.

## 4. Discusión: monotonía en semanas de 6 días

La primera versión del motor llenaba las semanas de 6 días hasta el límite de tiempo con días casi iguales, y la monotonía
de Foster (media / desviación de la carga diaria) superaba 2 en los tres perfiles de 6 días (hasta 2,42). Se corrigió
haciendo que el motor elija entre semanas candidatas (DISENO §5.5); ahora cumplen 17 de los 18 casos.

Quedaba uno: el perfil con tope de intensidad ligera-moderada y adherencia baja (2,26 en la semana 17). Ahí la dosis
(135 min) queda pegada al suelo de la OMS y el tope solo permite sesiones moderadas. En los tres días sin fuerza caben
128 min equivalentes como mucho (60 min por día, y el calentamiento en Z1 no cuenta), así que hacía falta aeróbico
también en los días de fuerza. El motor solo probaba dos repartos: aeróbico en todos los días de fuerza (seis días casi
iguales) o un día de descanso más (no llega a la dosis). Ceder tiempo de fuerza y cargar más los días de fuerza tampoco
lo resolvían.

**Solución (2026-10-29):** entre las semanas candidatas se prueba también con aeróbico solo en algunos días de fuerza (los
de más tiempo libre) o en ninguno. Con uno basta: 135 min equivalentes y monotonía 1,71, porque se alternan días con más
carga y días solo de fuerza. Si no hay aeróbico en ningún día de fuerza, los días libres se reparten por igual, ya que la
alternancia la dan los de fuerza. Cambia el reparto en 53 de las 384 semanas de referencia y todos los criterios siguen
cumpliéndose. [Inferencia: el orden de preferencia entre candidatas]

## 5. Limitaciones

- Los perfiles son sintéticos y la adherencia es constante por simulación; la real varía semana a semana.
- Sin intervals.icu, el umbral se estima con la FC media de tu sesión más dura de 20–70 min. Solo se ha contrastado con
  una cuenta (166 frente a 174 configurados): tiende a quedarse corto, porque la media incluye el calentamiento. Las
  zonas salen algo más suaves de lo debido; hace falta contrastarlo con más personas antes de afinarlo.
- El sueño en horas se compara con la mediana de los 28 días anteriores; los umbrales (menos de 6 h y una o más por debajo
  de lo normal) son **[Inferencia]**.
- Varios parámetros son **[Inferencia]** (punto de partida 60/80/100 %, progresión del 10 %, umbrales de adherencia,
  factores de días duros y suaves, partes del día para la fuerza): la validación comprueba que el resultado cumple las
  guías, no que esos valores sean óptimos.
- Los minutos equivalentes se calculan por zonas de FC (Z1 no cuenta, Z4+ cuenta doble); sin reloj, la zona real depende de
  que el usuario siga la prueba del habla.
