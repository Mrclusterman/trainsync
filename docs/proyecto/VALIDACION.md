# Validación del motor de planes de salud — TrainSync (Fase D)

> **Fecha:** 2026-10-21 · Reproducible: `cd backend && python -m validation.run` (con `--real` y las credenciales de
> intervals.icu en el entorno, añade el caso real). Resultados completos: `validacion/resultados.md` y `resultados.csv`.

## 1. Método

Se distinguen tres niveles, de menor a mayor alcance:

1. **Verificación** (¿el código hace lo que dice el diseño?): 172 tests automáticos (`pytest`) sobre las reglas del
   diseño, el cribado del ACSM (sus ocho ramas), la API, las cuentas y los derechos RGPD.
2. **Validación por simulación** (¿los planes cumplen la evidencia?): 6 perfiles × 3 niveles de adherencia (95 %,
   75 % y 40 % de las sesiones hechas) durante 24 semanas (6 bloques), contra los criterios V1–V11 de `DISENO.md` §11.
   Perfiles: principiante sedentario, persona de 70 años, gimnasio (fuerza y músculo), corredor avanzado, pérdida de
   grasa y pérdida de grasa con tope de intensidad del cribado.
3. **Caso real**: el motor con la fisiología de una cuenta real de intervals.icu (cuenta de pruebas del autor).

Queda fuera, como trabajo futuro, la **validación de eficacia** (si las personas mejoran su forma física siguiendo los
planes): exige un estudio con participantes y aprobación de un comité de ética.

## 2. Resultados

**101 de 110 comprobaciones aplicables cumplen**; 10 no aplican.

| Criterio | Resultado |
|---|---|
| V1 Suelo OMS (≥ 150 min equivalentes y ≥ 2 días de fuerza) en semanas estables | Cumple en todos los casos aplicables. No aplica en 10: sin semanas estables con adherencia media o baja (la progresión aún no ha llegado a la meta), o perfiles en los que es físicamente imposible (el principiante tiene 150 min a la semana en total, fuerza incluida) |
| V2 Mayores de 65: equilibrio y fuerza ≥ 3 días | Cumple |
| V3 Fuerza y músculo: cada patrón ≥ 2 veces por semana; series en la dosis o aviso | Cumple |
| V4 Pérdida de grasa: ≥ 250 min o aviso | Cumple |
| V5 Subida semanal ≤ 10 % y descarga cada 4.ª semana | Cumple |
| V6 Tope del cribado: sin Z4+ ni RIR < 3 | Cumple |
| V7 Descanso semanal; pierna pesada nunca en días seguidos | Cumple |
| V8 Fuerza y músculo: sin carrera el día de pierna | Cumple |
| V9 Monotonía de Foster < 2 | **No cumple en los 3 perfiles de 6 días** (máx. 2,03–2,42); cumple en los demás (máx. 1,58) |
| V10 Determinismo | Cumple |
| V11 Adherencia baja → menos volumen que alta | Cumple |
| Caso real | Cumple: LTHR 174, FC máx. 192 → techo fácil 144 ppm = 75 % de la FC máx.; objetivos de 50 a 75 % |

## 3. Defectos que encontró la validación (y su corrección)

La simulación hizo su función: la primera ejecución dio 106 de 120 y destapó defectos que los tests unitarios no veían.

| Defecto | Causa | Corrección |
|---|---|---|
| Subidas semanales de hasta +11,6 % | El 10 % se aplicaba al valor sin redondear; con minutos pequeños el redondeo lo superaba | Cada semana se calcula sobre la anterior ya redondeada y hacia abajo |
| Con adherencia baja sostenida el volumen bajaba sin límite | La revisión del bloque no tenía mínimo | Mínimo de 60 min (OMS: algo de actividad es mejor que nada) |
| Las sesiones de calidad no aparecían | Si el deporte no tenía ese tipo (caminar no tiene VO2max) o no cabía, se pasaba directamente a aeróbico | Se prueba el otro tipo de calidad antes |
| La fuerza dejaba sin tiempo a la resistencia (35 min equivalentes en semanas estables) | Al subir las series, la sesión de fuerza llenaba el día | El suelo aeróbico de la OMS va antes que seguir subiendo series: la fuerza ocupa una parte menor del día hasta llegar al suelo |
| El día "suave" no acortaba | La duración se corregía multiplicando y se pasaba hasta el tope; la fuerza ya en 1 serie no podía bajar | Corrección aditiva de la duración; quitar un accesorio si ya hay 1 serie |

También se corrigieron dos errores del propio protocolo de validación: excluir las semanas con aviso dejaba V1 y V9 sin
comprobar casi nada (pasaban "en vacío"). V1 se evalúa ahora contra el suelo de la OMS, y la imposibilidad física se
estima con una fórmula independiente del motor, para no validar el motor consigo mismo.

## 4. Discusión: monotonía en semanas de 6 días

Con 6 días de duración parecida y uno de descanso, la monotonía de Foster (media / desviación de la carga diaria) supera
2 aunque se alternen días duros y suaves y la fuerza ondule (pesado / ligero / medio): esos perfiles tienen la semana
llena hasta el límite de tiempo (la dosis no cabe) y variar más la carga exigiría dejar días a medio usar, alejándose de
los minutos de la OMS. El motor prioriza la dosis.

Dos matices para la memoria: (1) el umbral de 2 procede del seguimiento de deportistas con cargas altas (Foster, 1998),
donde la monotonía alta se asocia a enfermedad junto con una carga elevada; en semanas de actividad moderada que no llegan
a la dosis su relevancia es menor [Inferencia]; (2) aun así es un resultado negativo que se declara, no se oculta. Mejora
propuesta (trabajo futuro): con 6 o más días disponibles, convertir uno en recuperación activa cuando la monotonía prevista
supere 2, y validar el efecto sobre la dosis.

## 5. Limitaciones

- Los perfiles son sintéticos y la adherencia es constante por simulación; la real varía semana a semana.
- Varios parámetros son **[Inferencia]** (punto de partida 60/80/100 %, progresión del 10 %, umbrales de adherencia,
  factores de días duros y suaves, partes del día para la fuerza): la validación comprueba que el resultado cumple las
  guías, no que esos valores sean óptimos.
- Los minutos equivalentes se calculan por zonas de FC (Z1 no cuenta, Z4+ cuenta doble); sin reloj, la zona real depende de
  que el usuario siga la prueba del habla.
