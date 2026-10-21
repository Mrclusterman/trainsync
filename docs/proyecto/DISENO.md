# Diseño del modelo — TrainSync (Fase B)

> **Fecha:** 2026-10-21 · Especificación del motor para objetivos sin fecha. Cada regla cita su base en
> `ESTADO_DEL_ARTE.md` (EA §n) o en `MOTOR_ENTRENAMIENTO.md` (MOTOR §n), con la etiqueta de certeza de allí; lo que no
> tiene base publicada se marca **[Inferencia]** y se somete a la validación de la fase D.
> El objetivo "evento" sigue el motor actual (MOTOR §1–§15); este documento lo integra como un objetivo más.

## 1. Principios de diseño

1. **Determinista y explicable.** Mismas entradas, mismo plan; cada decisión lleva su motivo visible. Sin IA en tiempo de uso.
2. **Suelo de salud común.** Todo plan, sea cual sea el objetivo, cumple las guías de la OMS (EA §1). El objetivo solo cambia
   cómo se reparte y cuánto se sube por encima de ese suelo.
3. **Constancia antes que complejidad** (ACSM 2026, EA §2): planes sencillos que se ajustan a la adherencia real.
4. **Sin fecha de fin.** Bloques de 4 semanas que se renuevan; el objetivo se puede cambiar en cualquier momento.
5. **Funciona con pocos datos.** Con reloj usa FC, HRV y sueño; sin reloj, sensación (RPE/RIR) y lo que el usuario registra.

## 2. Entradas

| Entrada | Valores | Origen |
|---|---|---|
| Objetivo | `event`, `fitness` (ponerse en forma), `strength` (fuerza y músculo). `fat_loss` (perder grasa) se retirado el 2026-10-21: con usuarios desde 14 años, un objetivo de perder peso cruza mal con los trastornos de la conducta alimentaria | Usuario |
| Fuerza | `gym`, `bodyweight`, `none` | Usuario |
| Resistencia | subconjunto de `walking`, `running`, `cycling`, `swimming` (puede estar vacío) | Usuario |
| Días disponibles | días de la semana + minutos por sesión | Usuario |
| Nivel | `beginner`, `intermediate`, `advanced` | Historial si hay datos (sesiones/semana en 12 semanas); si no, autodeclarado |
| Edad | años | Usuario (≥ 65 activa el componente de equilibrio) |
| Cribado | actividad actual, enfermedad conocida, síntomas, otras condiciones | Algoritmo del ACSM al empezar (EA §5) |
| Datos | intervals.icu, Strava, Polar, Apple Salud / Health Connect, FIT, manual | Vinculación (EA §8) |

**Caminar** se añade como deporte de resistencia: es la actividad aeróbica de entrada para principiantes y mayores. [Inferencia]

## 3. Dosis semanal por objetivo

Minutos aeróbicos en **minutos moderados equivalentes** (1 min vigoroso = 2 moderados, equivalencia de la OMS, EA §1).

| Objetivo | Aeróbico (meta del bloque estable) | Fuerza: días | Fuerza: series por grupo muscular y semana | Esfuerzo en fuerza | Base |
|---|---|---|---|---|---|
| Suelo (todos) | ≥ 150 min | ≥ 2 | ≥ 4 [Inferencia: 2 días × 2 series] | moderado o más | OMS (EA §1) |
| `fitness` | 150 → 300 min | 2–3 | 6 → 10 | RIR 2–3 | OMS, ACSM 2026 (EA §1–2) |
| `strength` | 150 min (bici o natación preferentes) | 3–4 (cada grupo 2 veces) | 10 → 20 | RIR 1–3 | ACSM 2026, Schoenfeld 2017, Schumann 2022 (EA §2–3) |
| ~~`fat_loss`~~ (retirado) | ~~150 → 250+ min~~ | ~~2–3~~ | ~~6 → 10~~ | ~~RIR 2–3~~ | Donnelly 2009, Willis 2012 (EA §4) |
| `event` | según MOTOR (TSS y rampa de CTL) | MOTOR §5 | MOTOR §5 | MOTOR §5 | MOTOR |
| Mayores de 65 (se suma) | — | equilibrio + fuerza ≥ 3 días | — | — | OMS (EA §1) |

- **Punto de partida** (semana 1): principiante 60 % de la meta del objetivo (mínimo 60 min aeróbicos y 2 días de fuerza
  con 1–2 series por grupo); intermedio 80 %; avanzado 100 %. [Inferencia, apoyada en la OMS: empezar poco y subir]
- **Intensidad aeróbica:** principiantes, solo moderada; el resto, distribución polarizada (≈ 80 % fácil) de MOTOR §1, con
  sesiones de umbral como trabajo de calidad. [Práctica: MOTOR]

## 4. Bloques sin fecha (mesociclos)

- Bloque = 4 semanas: 3 de carga + 1 de descarga (−30 % de volumen; patrón de MOTOR §8). [Práctica]
- **Progresión dentro del bloque** hacia la meta del objetivo: aeróbico +10 % por semana de carga; fuerza +1 serie por grupo
  y semana mientras no se supere la meta. [Inferencia: la regla del 10 % no tiene respaldo en ensayos; se valida en la fase D]
- **Revisión al acabar el bloque**, por adherencia (sesiones hechas / planificadas):
  - ≥ 85 % y esfuerzo percibido medio dentro de lo previsto → el siguiente bloque parte de un 5–10 % más;
  - 60–85 % → se repite el volumen;
  - < 60 % → baja un 20 % y se quita un día si sobran. [Inferencia: la constancia manda (ACSM 2026) y los abandonos se
    concentran en la subida de volumen (EA §6)]
- Alcanzada la meta, el bloque estable alterna estímulos (rotación del catálogo) para mantener sin subir más.

## 5. Reparto semanal

Entrada: días disponibles, minutos por sesión, dosis de la semana. Algoritmo determinista:

1. **Descanso:** al menos 1 día sin sesión. [Inferencia]
2. **Fuerza:** se colocan los días de fuerza lo más separados posible (≥ 48 h entre sesiones del mismo grupo).
   ≤ 3 días → cuerpo completo; 4 días → torso/pierna. [Práctica, coherente con 2 veces por grupo (ACSM 2026)]
3. **Resistencia:** en los días restantes; si faltan días, se combina con fuerza en el mismo día.
4. **Orden en días combinados:** primero lo prioritario del objetivo (`strength` → fuerza antes; `fitness` → la
   sesión aeróbica de calidad antes). En `strength`, sin carrera intensa el mismo día que pierna y, si hay deportes
   alternativos, preferir bici o natación. [Schumann 2022, Lundberg 2022 (EA §3); Inferencia en el detalle]
5. **Elección de la semana:** se prueban semanas candidatas y se elige la primera que cumple el suelo aeróbico de la OMS
   con monotonía de Foster < 2. Orden de preferencia: la fuerza ocupa todo su tiempo antes que menos (no se quita fuerza
   sin necesidad), y para cada caso, la semana tal cual antes que con un día libre convertido en descanso (con una nota
   que lo explica en lenguaje llano). El usuario puede recuperar ese día con un toque y entonces el motor no lo vuelve a
   quitar esa semana. Si ninguna cumple ambas cosas, prima el suelo de la OMS. [Foster 1998; orden: Inferencia]
6. **Duración:** cada sesión cabe en los minutos disponibles; si la dosis no cabe en los días dados, el plan lo dice
   en vez de alargar sesiones sin límite. Solo es un aviso si no llega al mínimo de la OMS (150 min) y la persona no es
   principiante; si no, es información en tono neutro. Los minutos suaves (Z1) no cuentan para la OMS, pero la app los
   muestra como actividad que también suma ("all physical activity counts", ficha de la OMS 2024). [Inferencia en el
   tono]

## 6. Sesión de fuerza

- **Patrones de movimiento** en lugar de músculos sueltos: sentadilla, bisagra de cadera, empuje horizontal y vertical,
  tracción horizontal y vertical, zancada/unilateral, core; con biblioteca por material (`gym` / `bodyweight`). Cuerpo completo =
  un ejercicio por patrón principal. [Práctica]
- **Prescripción por objetivo y nivel:** principiante 10–15 repeticiones a RIR 3; `fitness` 8–12 a RIR 2–3;
  `strength` 6–12 a RIR 1–2, con una serie pesada (≈ 4–6 repeticiones) en los multiarticulares para fuerza máxima.
  [ACSM 2009 y 2026 (EA §2); rangos exactos: Inferencia]
- **Progresión doble:** al completar el tope de repeticiones con el RIR objetivo en todas las series, subir la carga un 2–10 %
  y volver al mínimo de repeticiones. [ACSM 2009 (EA §2)]
- **Carga por RIR, no por % de 1RM** (la mayoría no conoce su 1RM). [Zourdos/Helms 2016 (EA §2)]

## 7. Sesión de resistencia

- Catálogo actual (MOTOR §4) para correr, bici y natación, más sesiones de caminar (paseo ligero, caminata a paso vivo,
  caminata con cuestas).
- **Intensidad sin reloj:** prueba del habla y RPE (moderado = se puede hablar pero no cantar; vigoroso = solo frases
  cortas). Con reloj, zonas de FC o ritmo de MOTOR §13–14. [Práctica, coherente con la OMS]

## 8. Ajuste diario

- **Con datos de recuperación** (HRV, sueño, TSB): las reglas de MOTOR §3 (bajar intensidad con HRV fuera de rango o mal
  sueño; vuelta a la base tras 7 días sin entrenar).
- **Sin reloj:** tres preguntas al abrir la app (cómo dormiste, energía, agujetas, de 1 a 5). Dos o más en 1–2 → sesión suave;
  todas en 1 → descanso activo. [Inferencia; se valida en la fase D]

## 9. Seguridad

- **Aviso legal y de salud** (decisión del 2026-10-21) al crear el perfil: no es un servicio médico, cuándo consultar
  y cuándo parar. Sin cuestionario, la intensidad la limitan el nivel (principiantes, solo moderada) y el aviso.
- **Cribado del ACSM, opcional y sin formulario** (para integraciones futuras), en lugar del PAR-Q+ (su licencia no permite incluirlo en una app sin permiso escrito, EA §5).
  El resultado fija el tope de intensidad del plan: ligera-moderada (resistencia sin Z4+ y fuerza a RIR ≥ 3), moderada
  (sin vigorosa, RIR ≥ 2) o vigorosa (sin tope). Activo con síntomas → el plan queda vacío y pide parar y consultar.
  Consulta recomendada → aviso visible y tope ligero-moderado hasta que se confirme. [EA §5]
- Avisos que no se ocultan: dolor en el pecho, mareo o dolor articular agudo → parar y consultar. [Práctica: PAR-Q+]
- Nada de dietas ni consejo médico (`docs/CUMPLIMIENTO.md`).

## 10. Datos e integraciones

Todas las fuentes se normalizan a dos estructuras: **actividad** (fecha, deporte, duración, distancia, FC media, tiempo en
zonas, carga, RPE) y **bienestar** (sueño, HRV, FC en reposo). El motor solo lee esas dos, así que da igual de qué marca
vengan. Orden de prioridad cuando hay varias: datos del reloj > Strava > registro manual. [Inferencia]

## 11. Criterios de validación (para la fase D)

Cada criterio se comprueba con perfiles simulados (principiante sedentario, persona mayor, gimnasio, corredor, pérdida de
grasa, cribado con tope) durante 24 semanas:

| # | Criterio | Fuente |
|---|---|---|
| V1 | En el bloque estable, todo plan cumple el suelo OMS (≥ 150 min aeróbicos equivalentes y ≥ 2 días de fuerza) | EA §1 |
| V2 | Mayores de 65: ≥ 3 días con equilibrio y fuerza | EA §1 |
| V3 | Cada grupo muscular se trabaja ≥ 2 veces por semana en `strength` y las series semanales quedan en la meta | EA §2 |
| ~~V4~~ | Retirado con el objetivo `fat_loss` (2026-10-21) | — |
| V5 | La subida semanal de volumen no pasa del 10 % y cada 4.ª semana es de descarga | Diseño §4 |
| V6 | Con tope ligero-moderado del cribado nunca aparece intensidad vigorosa, Z4+ ni RIR < 3; con "parar", ninguna sesión | EA §5 |
| V7 | Al menos 1 día de descanso por semana; nunca dos sesiones de pierna pesadas en días seguidos | Diseño §5 |
| V8 | En `strength`, nunca carrera intensa el mismo día que la sesión de pierna | EA §3 |
| V9 | La monotonía semanal (Foster) queda por debajo de 2 | MOTOR §3 |
| V10 | Determinismo: mismas entradas → mismo plan | Diseño §1 |
| V11 | Con adherencia simulada baja, el plan baja volumen en vez de subirlo | Diseño §4 |

Además: comparación de contenido con las guías y con planes de referencia publicados, y ejecución con la cuenta real de pruebas.
