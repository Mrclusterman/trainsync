# Plan del proyecto — Motor de entrenamiento de TrainSync

> **Fecha:** 2026-10-21 · **Entregable final:** la memoria del proyecto (`docs/proyecto/MEMORIA.md`). Todo lo que la memoria
> afirme tiene que estar respaldado por bibliografía verificada o por una validación ejecutada y reproducible en este repo.

## Decisiones de partida (Gabriel, 2026-10-21)

1. TrainSync deja de ser solo un planificador de carreras: es una **ayuda diaria de salud para todo tipo de público**.
   Si el único objetivo es un evento, al terminarlo desaparece el motivo para seguir usando la app.
2. **Objetivos:** preparar un evento (lo que ya existe), **ponerse en forma**, **fuerza y músculo** y **perder grasa** (este último retirado el 2026-10-21, `DISENO.md` §2).
   Los tres nuevos no tienen fecha: bloques continuos que se renuevan.
3. **Modalidades combinables:** solo gimnasio (o peso corporal), o gimnasio complementado con correr, nadar o bici.
4. **Sin depender de intervals.icu:** modo manual por sensación (RPE) e integraciones con las marcas de reloj más
   populares (estudiar qué APIs hay y en qué condiciones).
5. **El producto final es una app** (móvil + web). Cada usuario vincula una vez cada servicio (OAuth con token que se
   renueva solo); Apple Salud y Health Connect se leen desde la app móvil, que es la vía principal de integración.
   El alta de la app en cada programa de desarrolladores se hace una vez por marca (Polar inmediato; Suunto con acuerdo;
   Garmin solo empresas y con altas pausadas).
6. **Seguridad:** aviso legal y de salud visible al crear el perfil (decisión del 2026-10-21: un cuestionario no encaja
   con el público general). El cribado del ACSM queda en el backend como dato opcional, sin formulario. Nada de consejo médico ni dietas (ver `docs/CUMPLIMIENTO.md`).

## Fases

| Fase | Contenido | Resultado |
|---|---|---|
| A. Estado del arte | Guías de actividad física (OMS 2020, ACSM), fuerza (volumen, frecuencia, progresión, RIR), entrenamiento concurrente, pérdida de grasa, principiantes y mayores, PAR-Q+, adherencia, apps competidoras (Garmin, TrainingPeaks, Fitbod, Runna…), APIs de relojes | `docs/proyecto/ESTADO_DEL_ARTE.md`, cada afirmación con fuente y etiqueta de certeza |
| B. Diseño del modelo | Objetivos × modalidades × días disponibles; mesociclos sin fecha (bloques 3+1 con reevaluación); progresión de fuerza; dosis semanal por objetivo; estado diario opcional (HRV, sueño) y modo manual | Especificación en la memoria y en `MOTOR_ENTRENAMIENTO.md` |
| C. Implementación | Tipos de objetivo nuevos, generador de planes sin fecha, biblioteca de fuerza ampliada (rutinas por días), complemento de resistencia, PAR-Q+ en el backend, modelo de datos para el modo manual | Código + tests en `prototype/backend/` |
| D. Validación | 1) Verificación: tests unitarios y de propiedades. 2) Simulación: perfiles sintéticos (principiante, mayor, gimnasio, corredor, pérdida de grasa) durante N semanas, comprobando que se cumplen las guías (minutos/semana, días de fuerza, rampa de carga, distribución de intensidad, monotonía). 3) Validación de contenido frente a guías y planes de referencia, y frente a Garmin. 4) Caso real con la cuenta de pruebas | Scripts reproducibles en `prototype/backend/validation/` y resultados en la memoria |
| E. Memoria | Resumen (ES/EN), introducción, objetivos, estado del arte, metodología, diseño, implementación, validación y resultados, discusión, conclusiones, trabajo futuro, bibliografía, anexos | `docs/proyecto/MEMORIA.md` (exportable a PDF) |

## Límites declarados desde el principio

- La eficacia real en personas (¿mejora la forma física?) requiere un estudio con participantes y comité de ética: queda
  como trabajo futuro. La memoria valida que el modelo **cumple la evidencia y las guías**, no que produzca resultados.
- Las integraciones con marcas se estudian y diseñan; implementarlas depende de programas de socios (p. ej. Garmin
  Health API) que exigen solicitud y aprobación.
