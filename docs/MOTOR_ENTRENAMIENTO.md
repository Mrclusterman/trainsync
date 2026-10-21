# MOTOR DE ENTRENAMIENTO — TrainSync

> **Versión:** 0.6 — Días del usuario en la plantilla y emparejado de lo hecho con lo planificado (§15.1)
> **Fecha:** 2026-10-21
> **Estado:** Verificado por Claude contra bibliografía (sección 11). Cambios de §11.1 aprobados el 2026-10-19 y aplicados en Memoria v0.4
> **Complementa a:** `MEMORIA_TECNICA.md` §8 (fórmulas de TSS, CTL/ATL/TSB, zonas, HRV). Este documento no las repite: define **qué sesiones existen, cómo se eligen y cómo se organizan**.

---

## 0. Cómo leer este documento

Cada regla lleva una etiqueta de certeza:

| Etiqueta | Significado |
|---|---|
| **[Memoria]** | Ya decidido en `MEMORIA_TECNICA.md`. No se discute aquí. |
| **[Verificado: ref]** | Respaldado por la referencia citada (sección 16). Se ha comprobado el resumen o el texto, no siempre el texto completo. |
| **[Práctica: fuente]** | Recomendación publicada de un entrenador o plataforma de referencia (Friel, TrainingPeaks, Daniels), sin ensayo controlado detrás. |
| **[Práctica]** | Práctica habitual de entrenadores; no se ha encontrado fuente concreta ni ensayo. |
| **[Inferencia]** | Decisión de diseño de TrainSync. Se buscó evidencia y no la hay; es un parámetro razonable que se podrá ajustar con datos reales. |

Las [Inferencia] que quedan son las que **no tienen evidencia publicada** después de buscarla (sección 11.2), no las que faltan por revisar.

---

## 1. Principios

1. **Polarizado 80/20 con VO2max temprano.** [Memoria] Respaldo: la mayoría de atletas de resistencia bien entrenados hacen ~80 % de sesiones a baja intensidad [Verificado: Seiler 2010]; en corredores subélite, más volumen en Z1 mejoró más el rendimiento que más tiempo entre umbrales [Verificado: Esteve-Lanao 2007]; en 9 semanas, el modelo polarizado mejoró más VO2peak y tiempo hasta el agotamiento que umbral, HIIT o alto volumen [Verificado: Stöggl & Sperlich 2014]. *Limitación:* muestras pequeñas (12 y 48 atletas). Metaanálisis posterior: el polarizado mejora algo más el VO2peak que otras distribuciones (SMD 0,24, 11 estudios) [Verificado: Oliveira 2024], pero en contrarreloj y en atletas no élite apenas hay diferencia con el modelo piramidal; polarizado y piramidal superan al modelo de umbral [Verificado, fuente secundaria: revisión de alcance de 2024].
   **Matiz importante:** en el modelo de 3 zonas de Seiler, la "zona gris" es todo lo que queda **entre el primer umbral (aeróbico) y el segundo** [Verificado: editorial Frontiers 2019]. Con las zonas de Friel eso incluye la Z3 **y la Z4 (94–99 % LTHR)**. Por tanto las sesiones de umbral Z4 hacen el plan **piramidal**, no estrictamente polarizado. Se mantienen porque la evidencia no muestra peor resultado que el polarizado, pero el motor las contabiliza como intensidad moderada (sección 6.2).
2. **La Z3 nunca es objetivo de una sesión.** [Memoria] Excepciones: simulacros de ritmo de carrera en fase PEAK cuando el ritmo objetivo cae en Z3 (p. ej. maratón) y progresivos de umbral.
3. **Fuerza obligatoria.** [Memoria] Respaldo: mejora la economía de carrera un 2–8 % sin afectar al VO2max [Verificado: Blagrove 2018]; la fuerza pesada es la intervención con más respaldo en ciclismo [Verificado: Rønnestad & Mujika 2014]; el entrenamiento de fuerza redujo las lesiones deportivas a menos de 1/3 [Verificado: Lauersen 2014].
4. **El estado del día manda sobre el plan.** [Memoria] El plan es una intención; el ajuste diario (TSB, HRV, sueño) puede bajarlo, nunca subirlo. En los estudios de entrenamiento guiado por HRV, el grupo guiado hizo **menos** sesiones duras con resultados iguales o mejores [Verificado: Kiviniemi 2007; Vesterinen 2016; Javaloyes 2019]; la ventaja media es pequeña [Verificado, fuente secundaria: metaanálisis Manresa-Rocamora 2021].
5. **Determinismo.** [Memoria, DD-05] Mismos datos de entrada → mismo plan. Sin aleatoriedad, sin LLM en la decisión.
6. **La FC manda sobre el ritmo.** [Práctica] Toda sesión de running y ciclismo sin potenciómetro se prescribe en ppm. Con calor, el ritmo se ralentiza para mantener la FC objetivo; la app lo indica en las notas de adaptación.

---

## 2. Estructuras de entrada

```python
AthleteInput = {
  "profile": {
    "active_sports": ["running", "cycling"],      # RF-09
    "strength": "gym" | "bodyweight" | "none",
    "rest_days": ["MON"],
    "available_minutes": {"TUE": 75, "SUN": 240}, # por día; ausente = sin límite
    "has_power_meter": False,
    "pool_lane_m": 25 | 50 | None,
    "experience": {"running": "intermediate", "cycling": "intermediate"},
  },
  "physiology": {"lthr": 174, "max_hr": 192, "rest_hr": 46,
                 "ftp": None, "css_s_100m": None, "updated_at": "2026-04-21"},
  "wellness_10d": [{"date", "ctl", "atl", "hrv", "sleepScore", "restingHR"}],
  "activities_28d": [{"date", "sport", "tss", "duration_s", "hr_zone_secs[]", "rpe"}],
  "goals": [{"name", "sport", "date", "priority": "A"|"B"|"C",
             "format": "olympic"|..., "distance_km", "target_time_s"}],
  "history": {"plan_start": "2026-07-01",
              "chosen_sessions": [{"date", "catalog_id"}]}  # elecciones del usuario (RF-05)
}
```

```python
SessionTemplate = {               # entrada del catálogo (sección 4)
  "id": "R-VO2-3",
  "sport": "running",
  "category": "vo2max",            # recovery|aerobic|long|neuromuscular|vo2max|threshold|race_specific|brick|test|technique
  "phases": ["base_early", "base", "build", "peak"],
  "min_capacity": "high",          # high|medium|low: capacidad del día mínima para proponerla
  "duration_min": [45, 65],        # rango; el motor elige dentro según volumen objetivo
  "blocks": [                      # calentamiento / principal / vuelta a la calma
     {"block": "calentamiento", "minutes": 15, "zone": "Z2"},
     {"block": "principal", "reps": 5, "minutes": 3, "zone": "Z5", "rest_minutes": 3, "rest_zone": "Z1"},
     {"block": "vuelta a la calma", "minutes": "fill", "zone": "Z1"}],
  "goal": "Elevar el techo aeróbico",
  "if_bad": "Si la FC no sube a Z5 en la 2.ª serie o te sientes mal: termina en Z2.",
  "if_good": "No añadas series. La calidad de hoy se mide en ejecución, no en volumen.",
}
```

`"minutes": "fill"` = el bloque absorbe la diferencia entre la duración elegida y la suma del resto.

---

## 3. Pipeline diario

```text
ENTRADA: AthleteInput, fecha D
1. ESTADO
   ctl, atl ← wellness[D] (o último disponible)          [Memoria 7.2]
   tsb ← ctl - atl
   hrv_status ← media móvil 7 d de ln(RMSSD) vs SWC personal (sección 3.1)  [cambia Memoria 8.4]
   sleep ← sleepScore[D] (None si no existe)
   streak ← días consecutivos con actividad hasta D-1
   gray_flags_7d ← nº de sesiones fáciles con zona gris en 7 días (sección 7)
   load_focus ← reparto de la carga de 28 días: aeróbico bajo / alto / anaeróbico (sección 12)
   days_since_activity ← días desde la última actividad

2. CAPACIDAD DEL DÍA → normal | low | rest_only
   rest_only  si tsb < -30                                   [Práctica: Friel — < -30 riesgo alto]
   low        si hrv_status == outside_swc o sleep < 50      [Verificado para HRV; sueño: Inferencia]
   normal     en otro caso (incluye tsb entre -10 y -30: zona productiva de carga)
   [cambia Memoria 8.4: TSB < -10 ya no baja la carga, ver 3.2]

3. FASE Y SEMANA
   goal ← objetivo prioritario (sección 8.3)
   phase ← base_early | base | build | peak | taper    (sección 8.1)
   week_kind ← load | recovery (3+1)                   [Memoria 8.0]

4. INTENCIÓN DEL DÍA
   slot ← plantilla semanal[phase][weekday]            (sección 6)
   sport ← deporte principal del slot                   (sección 6.3)

5. AJUSTE
   si slot es vo2max/threshold/neuromuscular → cubre el tipo de carga que más falta (sección 12)
   si capacity == rest_only           → slot = recovery (o rest)
   si capacity == low  y slot es calidad → slot = aerobic; si HRV fuera de SWC y sleep < 50 → recovery
   si sleep < 50                       → volumen × 0.7 [Memoria 8.4]
   si days_since_activity ≥ 7 y slot es calidad → slot = aerobic (vuelta a la base)   [Práctica: Garmin; 7 d: Inferencia]
   si streak ≥ 3 y tsb < -20 y slot es calidad → slot = aerobic   [Inferencia]
   si gray_flags_7d ≥ 2               → sesiones Z2 con techo explícito de FC (sección 7)

6. SELECCIÓN DE SESIÓN
   candidatas ← catálogo[sport][slot.category] ∩ phase ∩ capacity
   sesión ← candidatas[semana_del_plan mod len(candidatas)]   # rotación determinista
   duración ← volumen_objetivo_del_día (sección 9) acotado por duration_min y available_minutes

7. ALTERNATIVAS (RF-05, DD-08)
   para cada deporte activo ≠ sport:
     misma categoría; duración escalada para que TSS_alt ≈ TSS_principal (±15 %)

8. FUERZA (sección 5)
   si strength ≠ none y weekday ∈ días_fuerza[phase] y capacity == normal → añadir sesión de fuerza

SALIDA: {date, phase, capacity, reasons[], main, alternatives[], strength}
```

Los días 2–14 se generan con los pasos 3–8 sin el ajuste del paso 5 y con CTL/ATL **proyectados** (sección 9.3). [Memoria: RF-05 recalcula cada día]

### 3.1 Estado HRV (sustituye la regla de ±8 % de la Memoria §8.4)

```text
ln_rmssd      = ln(RMSSD de la mañana)
rolling7      = media de ln_rmssd de los últimos 7 días (mínimo 3 valores válidos)
baseline      = media de ln_rmssd de los 28 días anteriores a esa ventana
swc           = 0,5 × desviación típica de ln_rmssd en esos 28 días
within_swc    si |rolling7 − baseline| ≤ swc   → plan normal
outside_swc   en otro caso (por arriba o por abajo) → baja intensidad
sin datos     si < 3 valores en 7 días o < 14 en la línea base → no se ajusta por HRV
```

Por qué:
- **Media móvil de 7 días, no el valor diario.** El coeficiente de variación del RMSSD diario fue 14,5 % frente a 6,7 % de la media móvil [Verificado: Heikura 2015]. Con ±8 % sobre el valor diario, la regla de la Memoria saltaría casi a diario por ruido.
- **ln(RMSSD) y media móvil frente a SWC** es lo que recomienda la guía del Australian Institute of Sport [Verificado: AIS HRV best-practice].
- **Fuera de SWC en cualquier dirección → baja intensidad.** Es el protocolo de Vesterinen 2016 [Verificado: Heikura 2015, mismo protocolo]. La Memoria trataba "elevated" como permiso para subir; los estudios no lo respaldan.
- **0,5 × SD** como SWC: [Verificado, fuente secundaria: resumen de HRV4Training de un estudio en ciclistas]; no confirmado en el texto original.
- **3 valores por semana como mínimo** bastan para detectar cambios [Verificado: trabajo de Plews en triatletas citado por el repositorio AUT].

### 3.2 TSB (sustituye "TSB < -10 → reducir carga" de la Memoria §8.4)

| TSB | Significado | Acción | Fuente |
|---|---|---|---|
| > +25 | Demasiado fresco | Nada (si no es taper, avisar de pérdida de forma) | [Práctica: Friel] |
| +5 a +25 | Fresco, listo para competir | Plan normal | [Práctica: Friel] |
| −10 a +5 | Neutro | Plan normal | [Práctica: Friel] |
| −10 a −30 | **Zona productiva de carga** | Plan normal | [Práctica: Friel — "durante bloques de construcción, entre −10 y −30"] |
| < −30 | Riesgo alto | Solo recuperación | [Práctica: Friel] |

TSB en valor **absoluto**, no en % del CTL: el % exagera con CTL bajos. [Práctica: foros de intervals.icu]

---

### 3.3 RPE (esfuerzo percibido)

**¿Importa? Sí, con matices.**
- Las medidas subjetivas (bienestar, esfuerzo percibido) reflejan la carga aguda y crónica con más sensibilidad y consistencia que las objetivas (FC, marcadores sanguíneos) [Verificado: Saw 2016, revisión sistemática de 56 estudios].
- El sRPE (RPE × minutos) es una medida válida de carga, también en intervalos [Verificado: Foster 2001; Haddad 2017].
- La monotonía y el strain semanales del sRPE explicaron la mayoría de las enfermedades leves cuando cada atleta superaba sus umbrales individuales [Verificado: Foster 1998, resumen].
- Un RPE más alto a la misma intensidad aparece en la sobrecarga funcional [Verificado: estudio en triatletas 2017 y revisión de 2021 sobre marcadores de sobrecarga].
- Prescribir solo por RPE funciona en estudios pequeños, pero la relación RPE-umbral varía mucho entre personas: complementa a FC/ritmo, no la sustituye [Verificado: estudio piloto Biology of Sport 2015; variabilidad del RPE en el umbral].
- Con calor, la FC deriva al alza a ritmo constante (deriva cardiovascular) [Verificado: concepto fisiológico; fuentes divulgativas].

**Uso en el motor:**

| Uso | Regla | Etiqueta |
|---|---|---|
| Prescripción | Cada bloque lleva un RPE objetivo (CR10): Z1 1–2 · Z2 3–4 · Z3 5 · Z4 6–7 · Z5 8–9 · Z6 10 | [Práctica; 2 mmol ≈ 4,3 y 4 mmol ≈ 6,5 en CR10 en corredores, fuente secundaria] |
| Series cortas | En series Z5–Z6 de < 3′, la guía principal es el RPE: la FC tarda en subir | [Práctica] |
| Natación sin CSS | Se prescribe solo por RPE | [Práctica] |
| Señal de fatiga | RPE ≥ 6 en una sesión con < 10 % del tiempo en Z3+ en los últimos 2 días → sin intensidad hoy | [Verificado: dirección del efecto; umbrales: Inferencia] |
| Carga semanal | sRPE de 7 días, monotonía (media/DT) y strain (carga × monotonía) en `/state`; aviso si monotonía > 2 | [Verificado: Foster 1998; umbral 2: Práctica] |

**Datos:** se leen de intervals.icu, que ya pide el RPE al terminar la actividad (`icu_rpe`, 1–10, y `session_rpe`) [Verificado: campos presentes en la API real].

## 4. Catálogo de sesiones

Zonas según `MEMORIA_TECNICA.md` §8.3 (FC: % LTHR; natación: % CSS). Rango de duración pensado para nivel intermedio; principiante × 0.75, avanzado × 1.25 [Inferencia].

### 4.1 Running (17 tipos)

| ID | Categoría | Estructura principal | Duración | Fases | Cap. mín. | Etiqueta |
|---|---|---|---|---|---|---|
| R-REC | recovery | Continuo Z1 | 20–40′ | todas | low | [Práctica] |
| R-Z2 | aerobic | Continuo Z2 | 40–70′ | todas | medium | [Memoria] |
| R-Z2-STR | aerobic | Z2 + 6×20″ progresivos, 1′40″ andando | 45–60′ | base, build, peak | medium | [Práctica] |
| R-LONG | long | Continuo Z2 | 75–150′ | todas salvo taper | medium | [Memoria] |
| R-LONG-MP | race_specific | Larga Z2 con últimos 20–40′ a ritmo de carrera | 90–150′ | peak | high | [Práctica]; Z3 permitida solo si el ritmo de carrera cae ahí (maratón) |
| R-HILL-NM | neuromuscular | 8–10×10″ cuesta máxima, rec. completa andando | 40–50′ | base_early, base | medium | [Práctica] |
| R-HILL-VO2 | vo2max | 6–8×90″ cuesta Z5, rec. bajando al trote | 50–65′ | base_early, base, build | high | [Práctica] |
| R-VO2-3 | vo2max | 5×3′ Z5, rec. 3′ Z1 | 45–60′ | base_early, base, build, peak | high | [Memoria 8.4: "5×3min"] |
| R-VO2-2 | vo2max | 8×2′ Z5, rec. 2′ Z1 | 45–60′ | base_early, base | high | [Memoria 8.4: "8×2min"] |
| R-VO2-1K | vo2max | 5–6×1000 m Z5, rec. 2′30″ | 50–65′ | build, peak | high | [Práctica] |
| R-FARTLEK | vo2max | 10×1′ Z5 / 1′ Z1 | 40–55′ | base, build | high | [Práctica] |
| R-THR-CRUISE | threshold | 3–4×8–10′ Z4, rec. 2′ | 55–70′ | build, peak | high | [Práctica] |
| R-THR-2x15 | threshold | 2×15–20′ Z4, rec. 4′ | 60–75′ | build, peak | high | [Práctica] |
| R-THR-PROG | threshold | Progresivo: 20′ Z2 → 10′ Z3 → 10′ Z4 | 50–60′ | build | high | [Memoria 8.4: Z3 puntual en progresivos] |
| R-RACE | race_specific | Bloques a ritmo objetivo: 10K 3×2 km; media 2×5 km; maratón 2×8 km | 60–100′ | peak, taper (reducido) | high | [Práctica] |
| R-BRICK | brick | 10–30′ tras la bici: 5′ Z2 + ritmo de carrera | 10–30′ | build, peak | medium | [Práctica]; solo triatlón/duatlón |
| R-TEST-LTHR | test | 30′ máximo sostenible en solitario; LTHR = FC media últimos 20′ | 50′ | onboarding, cada 8–12 semanas | high | [Práctica: Friel] |

*Descartado:* 30″/15″ en carrera. Solo hay estudios agudos, y en mediofondistas muy entrenados los intervalos de 30″ dieron **menos** tiempo por encima del 90 % del VO2max que los de 3′. No se encontraron ensayos de entrenamiento. [Verificado: búsqueda sin ensayos; estudio agudo]

### 4.1.b Trail (5 tipos, solo con objetivo trail)

Determinantes: en subida pesan la velocidad aeróbica máxima, la fuerza máxima y el peso; en bajada, la rigidez musculotendinosa [Verificado: Lemire 2021]. La bajada causa daño muscular excéntrico que dura días, y la exposición previa a bajadas lo reduce (efecto de carga repetida) [Verificado: Bontemps 2021, revisión]. Con 5–10 sesiones de bajada baja el dolor, no la fatiga [Verificado: Bontemps 2024]. Entrenadores recomiendan bajadas rápidas cada ~2 semanas [Práctica].

| ID | Categoría | Estructura | Fases |
|---|---|---|---|
| T-HILL-VO2 | vo2max | 6×3′ subida Z5, bajada al trote | todas salvo taper |
| T-HILL-THR | threshold | 3×10′ subida Z4; marcha rápida si > ~15 % | build, peak |
| T-LONG | long | Por tiempo en desnivel, bajadas controladas | todas salvo taper |
| T-DOWNHILL | neuromuscular | 6×2′ de bajada viva, subida andando | base, build |
| T-RACE-SIM | race_specific | Recorrido similar con subidas a ritmo de carrera | peak |

Las sesiones trail alternan con las de running por rotación semanal, así la bajada sale cada ~2 semanas. [Inferencia]

### 4.1.c Neuromusculares y tests (todos los deportes)

| ID | Categoría | Estructura | Fases |
|---|---|---|---|
| R-HILL-NM | neuromuscular | 8–10×10″ cuesta máxima, rec. completa | base_early, base, build |
| C-SPRINT | neuromuscular | 6–8×10″ sprint, 4′ entre sprints | base, build, peak |
| S-SPRINT | neuromuscular | 8×25 máximo, desc. 45″ | base, build |
| R-TEST-LTHR · C-TEST · S-TEST-CSS | test | Protocolos de §4.1–4.3 | cualquiera |

Los tests sustituyen la primera sesión de calidad del plan cuando faltan datos fiables (LTHR implausible o ausente, sin CSS). [Memoria RF-03]

### 4.2 Ciclismo (17 tipos)

| ID | Categoría | Estructura principal | Duración | Fases | Cap. mín. | Etiqueta |
|---|---|---|---|---|---|---|
| C-REC | recovery | Continuo Z1, cadencia libre | 30–60′ | todas | low | [Práctica] |
| C-Z2 | aerobic | Continuo Z2 | 60–150′ | todas | medium | [Memoria] |
| C-Z2-CAD | aerobic | Z2 + 6×2′ a 100–110 rpm | 60–90′ | base_early, base | medium | [Práctica] |
| C-Z2-TORQUE | aerobic | Z2 + 5×5′ a 50–60 rpm en subida, FC en Z2 | 75–120′ | base, build | medium | [Inferencia: fuerza-resistencia sin pasar a Z3] |
| C-LONG | long | Continuo Z2, nutrición practicada | 150–300′ | todas salvo taper | medium | [Memoria] |
| C-SPRINT | neuromuscular | Z2 + 6–8×10″ sprint, 4′ entre sprints | 60–90′ | base, build, peak | medium | [Práctica] |
| C-VO2-3015 | vo2max | 3×13′ (30″ Z5–Z6 / 15″ suave), 3′ entre series | 75–90′ | base_early, base, build | high | [Verificado: Rønnestad 2015] |
| C-VO2-4x5 | vo2max | 4×5′ Z5, rec. 2′30″ | 60–80′ | base_early, base, build, peak | high | [Verificado: protocolo LI de Rønnestad 2015] |
| C-VO2-5x4 | vo2max | 5×4′ Z5, rec. 4′ | 70–90′ | base, build, peak | high | [Práctica] |
| C-VO2-4020 | vo2max | 3×10′ (40″ Z5–Z6 / 20″ suave), 5′ entre series | 70–90′ | build, peak | high | [Práctica] |
| C-THR-3x12 | threshold | 3×12′ Z4, rec. 4′ | 75–100′ | build, peak | high | [Práctica] |
| C-THR-2x20 | threshold | 2×20′ Z4, rec. 5′ | 80–110′ | build, peak | high | [Práctica] |
| C-THR-OU | threshold | 3×(4×(2′ Z4 / 1′ Z5)), 5′ entre series | 75–95′ | build, peak | high | [Práctica] |
| C-CLIMB | threshold | 2–4 subidas de 8–20′ en Z4, bajada de recuperación | 90–180′ | build, peak | high | [Práctica]; usa puertos locales si el perfil los define |
| C-RACE-SIM | race_specific | Larga con 2–3 bloques de 20–30′ a ritmo de evento | 180–300′ | peak | high | [Práctica]; Z3 permitida si el evento es gran fondo |
| C-OPENER | race_specific | 3×3′ Z4 a ritmo de carrera, rec. 3′ | 45–75′ | taper | high | [Práctica]; añadida en v0.3: el taper de ciclismo no tenía sesión race_specific |
| C-BRICK | brick | Bici a ritmo de carrera + R-BRICK inmediato | 60–150′ | build, peak | high | [Práctica]; solo triatlón/duatlón, sábados de build/peak |
| C-LONG-LATE | long | Larga Z2 con 2×10′ Z4 en la última hora | 180–300′ | build, peak | high | [Verificado: la durabilidad (rendir con fatiga) decide las fases finales de pruebas largas — revisión Frontiers 2025; sesión: Práctica] |
| C-TEST | test | LTHR 30′ (sin potencia) · FTP 20′ o rampa (con potencia) | 50–60′ | onboarding, cada 8–12 semanas | high | [Memoria 7.4] |

### 4.3 Natación (16 tipos)

Sin CSS conocido, las zonas se prescriben por RPE (Z2 = 4–5/10, Z4 = 7/10, Z5 = 8–9/10) hasta hacer `S-TEST-CSS`. [Inferencia]

| ID | Categoría | Estructura principal | Volumen | Fases | Cap. mín. | Etiqueta |
|---|---|---|---|---|---|---|
| S-REC | recovery | Continuo Z1 + técnica suave | 1.000–1.500 m | todas | low | [Práctica] |
| S-TECH | technique | Ejercicios: *catch-up*, 6 patadas/brazada, *sculling*, pull buoy; Z1–Z2 | 1.500–2.000 m | todas | low | [Práctica] |
| S-AERO | aerobic | 4–6×400 Z2, desc. 20″ | 2.000–3.000 m | todas | medium | [Práctica] |
| S-LONG | long | Continuo 1.500–3.000 m Z2 | 2.000–3.500 m | todas salvo taper | medium | [Práctica] |
| S-PULL | aerobic | 6×200 pull buoy + palas Z2 | 2.000–2.500 m | base, build | medium | [Práctica] |
| S-KICK | technique | 8×50 patada Z2 dentro de sesión aeróbica | 1.800–2.200 m | base | medium | [Práctica] |
| S-CSS-100 | threshold | 8–12×100 a CSS (Z4), desc. 10–15″ | 2.000–2.800 m | base, build, peak | high | [Práctica: formato habitual de entrenamiento por CSS] |
| S-CSS-200 | threshold | 5×200 Z4, desc. 20″ | 2.200–2.800 m | build, peak | high | [Práctica] |
| S-THR-400 | threshold | 3×400 Z4, desc. 30″ | 2.400–3.000 m | build, peak | high | [Práctica] |
| S-PYRAMID | threshold | 100-200-300-200-100 Z4, desc. 20″ | 2.200–2.600 m | build | high | [Práctica] |
| S-VO2-50 | vo2max | 16×50 Z5, desc. 15–20″ | 1.800–2.400 m | base_early, base, build | high | [Práctica] |
| S-VO2-100 | vo2max | 8×100 Z5, desc. 30″ | 2.000–2.600 m | base, build, peak | high | [Práctica] |
| S-SPRINT | neuromuscular | 8×25 Z6, desc. 45″ | 1.500–2.000 m | base, build | medium | [Práctica] |
| S-OW-LONG | long | Continuo en aguas abiertas, orientación y rebufo | 40–90′ | build, peak | medium | [Práctica: guías de USA Swimming y entrenadores; sin ensayos] |
| S-OW | race_specific | Aguas abiertas: Z2 con orientación cada 6–9 brazadas + 4×3′ Z4 | 30–60′ | build, peak | high | [Práctica]; solo con `swimming_open` activo |
| S-RACE | race_specific | Distancia de triatlón a ritmo de carrera, dividida en 2–3 bloques | distancia × 0.6–1.0 | peak, taper | high | [Práctica]; taper añadido en v0.3 |
| S-TEST-CSS | test | 400 m + 200 m máximos; CSS (s/100 m) = (T400 − T200) / 2 | 1.500 m | onboarding, cada 8–12 semanas | high | [Verificado: velocidad crítica, Wakayoshi 1992; protocolo 200/400 fiable en los 4 estilos, error típico ≤ 0,04 m/s (estudio 2023); sobreestima algo frente a protocolos más largos] |

---

## 5. Biblioteca de fuerza

### 5.1 Dosis por fase

| Fase | Objetivo | Series × reps | Intensidad | Sesiones/semana | RPE plan. | Etiqueta |
|---|---|---|---|---|---|---|
| base_early (semanas 1–3) | Adaptación anatómica | 2–3 × 12–15 | moderada | 2 | 6 | [Práctica] |
| base | Fuerza máxima | 3–4 × 4–8 RM | pesada | 2 | 7–8 | [Verificado: Rønnestad 2010 — 3×4–10 RM, 2/semana, 12 semanas] |
| build | Fuerza máxima + explosiva/pliometría | 3 × 4–6 RM + 3 × 5–8 saltos | pesada / explosiva | 2 | 7 | [Verificado: Rønnestad & Mujika 2014; Blagrove 2018] |
| peak | Mantenimiento | 2–3 × 4–6 RM | pesada, bajo volumen | 1 | 6 | [Verificado: mantenimiento en periodo competitivo, Rønnestad 2010b] |
| taper | — | — | — | 0 | — | [Memoria RF-08]. Compatible con la evidencia: la mejora de economía de carrera por fuerza se mantuvo ≥ 4 semanas sin fuerza (estudio pequeño, n = 8) [Verificado, fuente secundaria: Outside] |

Carga: `rTSS = (RPE/10)² × horas × 100` [Memoria 8.1]. El RPE real introducido tras la sesión sustituye al planificado.

**Sobre la fórmula:** el método validado es el sRPE de Foster, `RPE × minutos` en unidades arbitrarias [Verificado: Foster 2001; 36 estudios de validez revisados en Haddad 2017]. No existe una conversión publicada de sRPE a escala TSS (búsqueda sin resultados). La fórmula cuadrática de la Memoria se mantiene porque produce valores coherentes con el TSS (1 h a RPE 10 ≈ 100, como 1 h a umbral), pero es [Inferencia]. **Decisión:** guardar también el sRPE bruto (`rpe × minutos`) para poder recalibrar con datos del propio usuario.

### 5.2 Ejercicios

**Tren inferior (running, ciclismo, duatlón):**

| Patrón | Gimnasio | Peso corporal |
|---|---|---|
| Sentadilla | Sentadilla con barra / media sentadilla | Sentadilla búlgara, progresión a pistol |
| Bisagra | Peso muerto rumano | Peso muerto rumano a una pierna |
| Unilateral | Zancada búlgara con mancuernas, step-up con carga | Step-up alto, zancada inversa |
| Gemelo/sóleo | Elevación de gemelo de pie y sentado | Gemelo a una pierna en escalón (rodilla recta y flexionada) |
| Cadera | Hip thrust con barra | Puente de glúteo a una pierna |
| Pliometría (solo build) | Saltos al cajón, *drop jumps* bajos | Pogo jumps, saltos de rana, *bounding* |

**Tren superior y core (natación, triatlón):**

| Patrón | Gimnasio | Peso corporal |
|---|---|---|
| Tirón vertical | Jalón al pecho, dominadas lastradas | Dominadas / dominadas negativas |
| Tirón horizontal | Remo con mancuerna | Remo invertido |
| Empuje | Press de banca con mancuernas | Flexiones |
| Hombro (salud) | *Face pull*, rotación externa con polea | Rotación externa con goma, *band pull-apart* |

**Core (todos):** plancha frontal, plancha lateral, *dead bug*, Pallof press, plancha de Copenhague (aductores). [Práctica]

**Movilidad (opcional, perfil):** dorsiflexión de tobillo en pared, estiramiento de gemelo y sóleo. Si el perfil registra limitaciones biomecánicas, se añaden 10′ diarios al final de cualquier sesión. [Inferencia]

### 5.3 Reglas de programación de la fuerza

1. Mismo día que una sesión de calidad ("días duros duros, días fáciles fáciles"), preferiblemente en sesiones separadas (mañana/tarde). [Práctica]
2. **Orden si van seguidas:** hacer la fuerza antes mejora la fuerza dinámica de piernas (+6,9 %) sin afectar al VO2max [Verificado: Eddens 2018; otro metaanálisis de 2018 en J Sports Sci coincide]. Esos estudios no midieron la calidad de la sesión de intervalos posterior. **Regla:** en base (fase de fuerza máxima), fuerza primero; en build y peak, la sesión de resistencia primero. [Inferencia informada]
3. Nunca fuerza pesada de tren inferior el día antes de una sesión de calidad o de la tirada larga. [Inferencia, en línea con Memoria 8.0]
4. Sin fuerza si la capacidad del día no es `normal`. [Inferencia]
5. Running es la modalidad que más interfiere con las ganancias de fuerza; el ciclismo no mostró esa pérdida [Verificado: Wilson 2012, según el resumen]. Por eso, en días de fuerza, el deporte aeróbico preferido es ciclismo o natación cuando hay alternativa. [Inferencia]

---

## 6. Estructura semanal

### 6.1 Plantilla por fase (deporte único)

`Q` = calidad (vo2max / threshold / race_specific), `F` = fuerza, `—` = descanso.

| Fase | LUN | MAR | MIÉ | JUE | VIE | SÁB | DOM |
|---|---|---|---|---|---|---|---|
| base_early | — | Q vo2max + F | aerobic | Q vo2max | recovery + F | aerobic | long |
| base | — | Q vo2max + F | aerobic | aerobic (+ neuromuscular) | recovery + F | aerobic | long |
| build | — | Q threshold + F | aerobic | Q vo2max | recovery + F | aerobic | long |
| peak | — | Q race_specific + F | aerobic | Q vo2max | recovery | aerobic | long (race_specific cada 2 semanas) |
| taper | — | Q race_specific corta | aerobic corta | recovery | — | aerobic + activaciones | evento o aerobic |

- Máximo 2 sesiones de calidad por semana; 1 en semanas de descarga. [Verificado: el grupo de plan fijo de Vesterinen 2016 hacía 2–3 sesiones moderadas/intensas por semana; ~20 % de sesiones duras según Seiler 2010 con 6 sesiones ≈ 1–2]
- `rest_days` del perfil sustituyen los días de la plantilla. Si cae una Q en un día de descanso, se mueve al siguiente día libre que no sea anterior a la tirada larga. [Inferencia]
- Si `available_minutes` del día no da para la sesión, se elige la variante más corta de la misma categoría; si tampoco cabe, se intercambia con otro día de la semana. [Inferencia]

### 6.2 Distribución de intensidad

Se controla por **tiempo semanal** en tres bloques: bajo (Z1–Z2), moderado (Z3–Z4, la zona gris de Seiler) y alto (Z5–Z6). Las metas de la Memoria §8.4 (base_early 75/25, base 85/15, build 80/20, peak 75–80/20–25) se aplican a bajo frente a moderado+alto; dentro de la parte dura, el moderado no supera al alto salvo en build (piramidal). [Verificado: definición de Seiler; límite moderado ≤ alto: Inferencia] Si la semana planificada se desvía más de 5 puntos, el motor sustituye una sesión `aerobic` por `recovery` (exceso de calidad) o añade *strides*/sprints neuromusculares (defecto). [Inferencia]

### 6.4 Por tipo de prueba

| Prueba | Cambio sobre la plantilla | Etiqueta |
|---|---|---|
| Media, maratón, ultra (build) | Jueves umbral en lugar de VO2max | [Verificado parcial: los maratonianos de élite entrenan de forma más piramidal; Casado 2022] |
| Ultra (build, peak) | Sábado larga: dos largas seguidas | [Práctica; el volumen semanal predice el rendimiento en ultra — estudios observacionales] |
| Triatlón, duatlón (build, peak) | Sábado brick | [Práctica] |
| Trail | Sesiones T- en la rotación | §4.1.b |
| Natación | Deporte principal todos los días; nadadores de fondo más piramidales | [Verificado: González-Ravé 2021, evidencia débil] |
| Ciclismo de fondo | C-LONG-LATE en build/peak | §4.2 |

### 6.3 Multideporte

| Objetivo | MAR | MIÉ | JUE | VIE | SÁB | DOM | Fuente |
|---|---|---|---|---|---|---|---|
| Triatlón | Ciclismo Q | Natación | Running Q | Natación Q | Running + brick (build/peak) | Ciclismo long | [Inferencia]; reparto ≈ 10/55/35 % de la Memoria §8.4 |
| Duatlón | Running Q | Ciclismo | Ciclismo Q | Running | Running long | Ciclismo long | [Inferencia]; ≈ 40/60 % |
| Sin objetivo, varios deportes | Alterna el deporte principal por semanas | | | | | | [Práctica: alternar énfasis por semanas] |

Deportes no activos en el perfil se sustituyen por el primer deporte activo.

---

## 7. Zona gris

| Regla | Condición | Acción | Etiqueta |
|---|---|---|---|
| 1 | — | Ninguna plantilla tiene Z3 como zona objetivo, salvo las excepciones de §1.2 | [Memoria] |
| 2 | Sesión fácil (recovery/aerobic/long) con Z3 > 15 % del tiempo | Alerta "Esta sesión entró en zona gris"; el TSS real (mayor) entra en el ATL del día siguiente | [Memoria] |
| 3 | ≥ 2 alertas en 7 días | Aviso semanal + techo de FC explícito (límite alto de Z2) en todas las sesiones fáciles de la semana siguiente | [Memoria: patrón recurrente; umbral de 2 = Inferencia] |

**Techo de las sesiones fáciles.** El primer umbral (aeróbico) suele estar **20–40 ppm por debajo de la LTHR, ~30 ppm de media** [Práctica: Friel]. Con LTHR 174 eso da ~144 ppm, por debajo del límite alto de Z2 de running (89 % = 155 ppm). Por tanto, la parte alta de Z2 ya puede ser zona gris en sentido de Seiler. **Regla:** las sesiones `recovery`, `aerobic` y `long` muestran como techo `LTHR − 30` hasta que el usuario tenga un umbral aeróbico medido (test de 1–3 h a FC constante, protocolo de Friel). [Práctica: Friel; aplicación: Inferencia]

**Conflicto entre fuentes:** en running, `LTHR − 30` (144 ppm con LTHR 174) cae **por debajo** del inicio de la Z2 de Friel (85 % = 148 ppm). Las dos reglas de Friel no encajan entre sí (también se señala en foros). **Decisión:** en sesiones fáciles manda el techo; la Z2 mostrada se acota a `[inicio Z2, techo]` o a `≤ techo` si el techo queda por debajo. Se resuelve midiendo el umbral aeróbico. [Inferencia]

**Zonas por deporte.** Friel usa límites distintos para correr (Z1 < 85 %, Z2 85–89 %, Z3 90–94 %, Z4 95–99 %, Z5 ≥ 100 % LTHR) y para ciclismo (Z1 < 81 %, Z2 81–89 %, Z3 90–93 %, Z4 94–99 %, Z5 ≥ 100 %) [Práctica: Friel, vía TrainingPeaks y 220 Triathlon]. La Memoria §8.3 aplica a los dos deportes la tabla de ciclismo. **Cambio:** usar la tabla de running para running. Las zonas por defecto de intervals.icu también se basan en Friel [Práctica: foro de intervals.icu, no documentación oficial].

**Detalle de implementación:** intervals.icu entrega `icu_hr_zone_times` con las zonas que el atleta tenga configuradas allí, que pueden no coincidir con la tabla de la Memoria §8.3. El motor debe recalcular las zonas de TrainSync a partir de los límites de `sportSettings.hr_zones` o del stream de FC. [No verificado: estructura exacta de esos campos]

---

## 8. Periodización

### 8.1 Fases

El taper se fija en días según la prueba (§8.2). El resto se reparte con las proporciones de la Memoria §8.5 reescaladas: BASE ~52 %, CONSTRUCCIÓN ~28 %, PEAK ~20 %. Dentro de BASE, las 4 primeras semanas son `base_early`. [Memoria]

Sin `PLAN_START`, la preparación empieza 20 semanas antes del objetivo; antes de esa fecha, base de mantenimiento. [Inferencia]

Mínimos y máximos por duración total: [Inferencia]
- < 8 semanas: sin base_early; base 2 semanas; taper según §8.2.
- \> 24 semanas: base se alarga; build y peak se fijan en 8 y 4 semanas.

### 8.2 Taper

| Prioridad | Duración | Volumen | Intensidad / frecuencia | Etiqueta |
|---|---|---|---|---|
| A (running) | 5K/10K 7–10 días · 15K–media 11–14 · maratón 19–22 · ultra ~21 | Igual que la fila siguiente | Se mantienen | [Práctica: fisioterapeutas de Mayo Clinic; ultra: entrenadores]. En el código: 9 / 12 / 21 / 21 |
| A (resto) | 14 días | −41 a −60 %, reducción exponencial (semana −2: −35 %; semana −1: −60 %) | Se mantienen | [Verificado: Bosquet 2007 — 2 semanas, −41–60 %, intensidad y frecuencia constantes; Mujika & Padilla 2003 — frecuencia −20 % como máximo, reducción progresiva no lineal] |
| B | 3–5 días | −20 a −30 % | Se mantienen | [Práctica: Zwift Insider — carreras B/C con poca o ninguna puesta a punto, TSB −10 a 0]. Un taper de 6 días en mediofondistas no mejoró el rendimiento [Verificado: Mujika 2000, resumen] |
| C | 0–1 días | Solo el día previo suave | — | [Práctica: mismo origen]; la carrera cuenta como sesión de calidad de la semana |

### 8.3 Varios objetivos

1. Objetivo prioritario = el de mayor prioridad (A > B > C) entre los futuros; a igual prioridad, el más cercano. [Memoria RF-04]
2. Un objetivo B/C dentro del bloque de un A aplica su mini-taper y retoma la fase del A al día siguiente + 1 día de recuperación por cada 10 km de carrera a pie o cada 2 h de esfuerzo. [Inferencia]
3. Sin objetivo: fase `base` permanente con semanas 3+1. [Inferencia]

### 8.4 Especificidad de la sesión larga

**Running:** la tirada larga no supera el **25 % del volumen semanal** (30 % si se corren < 64 km/semana) **ni 150 minutos** [Práctica: J. Daniels, *Daniels' Running Formula*; cifra confirmada en varias fuentes secundarias, no en el libro]. Esta regla manda sobre la tabla.

Topes por objetivo: [running: Práctica (Daniels); resto: Inferencia — no se encontró evidencia]

| Objetivo | Tope de la larga |
|---|---|
| 5K | 75′ |
| 10K | 90′ |
| Media maratón | 120′ |
| Maratón | 150′ (nunca > 32 km) |
| Ultra (> 45 km) | 240′, con dos largas seguidas en build/peak |
| Gran fondo / ciclismo de distancia | 75–85 % de la duración prevista del evento, tope 5 h |
| Triatlón olímpico | Brick 90′ bici + 20′ carrera |
| 70.3 | Brick 3 h + 30′ |
| Ironman | Bici 5 h; carrera 2 h 30′ (separadas) |

---

## 9. Volumen y progresión

### 9.1 TSS semanal objetivo

Del modelo EWA de la Memoria §8.1, el CTL sube cada semana aproximadamente:

```text
ΔCTL_semana ≈ (TSS_medio_diario − CTL) × (1 − e^(−7/42)) ≈ (TSS_medio_diario − CTL) × 0,154
```

[Verificado: derivación matemática del EWA de la Memoria]. Despejando, para una rampa objetivo `r` (CTL/semana):

```text
TSS_semanal_objetivo = 7 × (CTL + r / 0,154)
```

La rampa depende del CTL actual, según la tabla de riesgo de TrainingPeaks [Práctica: TrainingPeaks; Friel propone +5 a +8 para la mayoría]:

| CTL actual | Rampa objetivo `r` (riesgo bajo) | Riesgo alto si ≥ | Ejemplo |
|---|---|---|---|
| ≤ 45 | +3 | +7 | CTL 40: 7 × (40 + 19,5) ≈ 417 TSS/semana |
| 45–70 | +4 | +8 | CTL 60: 7 × (60 + 26) ≈ 602 |
| ≥ 70 | +5 | +9 | CTL 80: 7 × (80 + 32,5) ≈ 788 |
| Semana de descarga | — | — | 0,6–0,7 × semana de carga anterior [Práctica: −30 a −40 % habitual en mesociclos 3:1] |

El patrón 3+1 no tiene ensayo que lo compare con otros (búsqueda sin resultados); se mantiene como práctica estándar. [Práctica]

El TSS semanal se reparte entre los días de la plantilla según pesos fijos: long 25 %, cada Q 15 %, aerobic 10–12 %, recovery 5 %, fuerza por su rTSS. Las duraciones se escalan dentro de `duration_min` hasta encajar el objetivo. [Inferencia]

**Límite de seguridad:** si la rampa real (`rampRate` de intervals.icu) alcanza el umbral de riesgo alto de su fila dos semanas seguidas, la siguiente semana pasa a descarga. [Práctica: TrainingPeaks para los umbrales; dos semanas: Inferencia]

### 9.2 TSS estimado de una sesión planificada

```text
TSS_plan = Σ_bloques  minutos/60 × IF_zona² × 100
IF_zona: Z1 0,65 · Z2 0,75 · Z3 0,88 · Z4 0,97 · Z5 1,05 · Z6 1,15   [Inferencia: puntos medios aproximados]
```

### 9.3 Proyección de 14 días

Para los días futuros, CTL y ATL se proyectan aplicando `next_load()` con el TSS planificado de cada día. El plan muestra el **TSB proyectado** el día del objetivo. Meta en prioridad A: **+15 a +25** (algunos atletas rinden mejor con +5 a +10; por encima de +25 se ha perdido forma). [Práctica: Friel, TrainingPeaks]

---

## 10. Caché e invalidación

Clave: `plan:{user_id}:{fecha}`. Se invalida cuando: [Memoria RF-05/Fase 3 + Inferencia]

| Evento | Invalida |
|---|---|
| Nueva actividad sincronizada | Hoy y días siguientes |
| Wellness de hoy cambia (HRV, sueño, CTL/ATL) | Hoy |
| El usuario elige una alternativa o registra RPE | Mañana y siguientes |
| Alta, edición o baja de objetivo | Todo el plan |
| Cambio de perfil (deportes, días, fuerza) o datos fisiológicos | Todo el plan |
| Cambio de fecha (medianoche, zona horaria del usuario) | Plan del día anterior |

---

## 11. Verificación (v0.2)

### 11.1 Cambios a la Memoria técnica (aprobados y aplicados en v0.4)

| # | Memoria dice | La evidencia dice | Propuesta |
|---|---|---|---|
| 1 | §8.4: TSB < −10 → reducir carga | Friel: −10 a −30 es la zona productiva en bloques de carga; el riesgo empieza en < −30 | Ajustar solo por debajo de −30 (§3.2) |
| 2 | §8.4: HRV del día vs media 7 d, ±8 %; "elevated" permite subir | El RMSSD diario es muy ruidoso (CV 14,5 %); los estudios usan media móvil 7 d vs SWC, y fuera de SWC en cualquier dirección → baja intensidad | Regla de §3.1 |
| 3 | §8.3: una tabla de zonas de FC para running y ciclismo | Friel usa tablas distintas; la de la Memoria es la de ciclismo | Tabla de running para running (§7) |
| 4 | §8.4: Z4 cuenta como "alta intensidad" del 80/20 | En el modelo de Seiler, Z4 bajo LTHR es zona intermedia (gris); con Z4 el plan es piramidal | Mantener Z4 en build, contarla como moderada (§1, §6.2) |

### 11.2 [Inferencia] que siguen sin evidencia tras buscarla

1. Umbral de sueño < 50 (Garmin sleepScore). La revisión de Fullagar 2015 confirma que la pérdida de sueño empeora el rendimiento específico, pero no da un umbral para un índice comercial.
2. Conversión de RPE a escala TSS para fuerza (§5.1).
3. Umbral de 2 alertas de zona gris en 7 días (§7).
4. Factores por nivel de experiencia (× 0,75 / × 1,25) y RPE para natación sin CSS.
5. Topes de la sesión larga en ciclismo y triatlón (§8.4).
6. Orden fuerza/resistencia en build y peak (§5.3).
7. Duración de la fase cuando faltan semanas (§8.1) y retorno tras objetivos B/C (§8.3).
8. Rotación multideporte y plantillas exactas (§6.1, §6.3).
9. Valores IF por zona para el TSS planificado (§9.2).

### 11.3 Comprobado y sin cambios

- Polarizado/piramidal frente a umbral, fuerza obligatoria y su dosis, mantenimiento en peak y eliminación en taper, taper de 2 semanas con −41–60 % (prioridad A), protocolo CSS 200/400, uso de sRPE como medida válida de carga.

## 12. Reparto de la carga (el "load focus" de Garmin)

Garmin elige la sesión sugerida según el tipo de carga que te falta: aeróbico bajo, aeróbico alto o anaeróbico
[Verificado: Garmin, "Daily Suggested Workouts"; Forerunner 965, "Training Status"]. Firstbeat no publica sus rangos
objetivo, así que TrainSync los toma de su propio modelo polarizado (§1, Memoria §8.4).

- **Medida:** la carga (`icu_training_load`) de cada actividad de los últimos 28 días se reparte según su tiempo en las
  7 zonas de FC de intervals.icu: Z1–Z2 aeróbico bajo, Z3–Z5a aeróbico alto, Z5b+ anaeróbico. [Inferencia: las zonas
  son las de intervals.icu; en carrera dependen de la LTHR que tenga guardada]
- **Objetivo** [Inferencia, desde el 80/20]: base 80 / 15 / 5 %, construcción y peak 75 / 20 / 5 %. En taper no se aplica.
- **Regla:** si el mayor déficit supera 5 puntos, la sesión de calidad del día (VO2max, umbral o neuromuscular) pasa a:
  - aeróbico bajo → `aerobic`;
  - anaeróbico → `anaerobic` (8×45″ por encima del VO2max);
  - aeróbico alto → `tempo` si el objetivo es media maratón o maratón en construcción o peak; si no, VO2max o umbral.
- Solo se aplica al día de hoy, que es el único con datos reales; los días siguientes muestran la plantilla.

## 13. Ritmos desde la FC (como Garmin)

Garmin propone las sesiones de carrera con ritmos calculados a partir de la FC. [Práctica: experiencia del usuario
con Garmin; Garmin no publica la fórmula. Verificado solo que los objetivos pueden ser FC o ritmo: manual del Forerunner 55]

- **Modelo:** recta velocidad = a + b·FC con los datos segundo a segundo (`/activity/{id}/streams`) de las 12 carreras
  más recientes: solo llano (|pendiente| ≤ 2 %), corriendo (> 1,5 m/s) y sin los 5 primeros minutos; mediana por tramos de
  5 ppm con ≥ 60 s; hacen falta ≥ 4 tramos y 15 ppm de rango. [Inferencia]
- **Uso:** los límites de FC de cada zona de TrainSync (§3, techo LTHR − 30 incluido) se convierten en ritmo con la recta.
  Con modelo, toda la carrera va por ritmo salvo trail (por FC), tests (por RPE) y Z6 (RPE). Sin modelo, la calidad usa
  el ritmo umbral de intervals.icu con las zonas de Friel [Práctica: Friel, "Quick guide to setting zones"] y el resto, FC.
- **Comprobación:** si el ritmo que la recta predice en la LTHR difiere más de un 5 % del ritmo umbral de intervals.icu,
  aparece un aviso: una de las dos está desajustada. [Inferencia]
- Caso real (2026-10-21, cuenta de pruebas): r = 0,96 con 12 carreras; a 174 ppm predice 5:08/km frente a 4:41/km de
  ritmo umbral, que corresponde a ~186 ppm.

## 14. Envío al reloj

`POST /plan/today/send` crea la sesión de hoy en el calendario de intervals.icu (`/events/bulk?upsert=true` con
`external_id` = `trainsync-AAAA-MM-DD`; reenviar la actualiza) y intervals.icu la pasa al reloj si está activado.

- **Una métrica por sesión:** según usuarios del foro de intervals.icu, al reloj solo llega la métrica principal; mezclar
  ritmo y FC pierde la segunda. [Práctica: foro de intervals.icu, sin confirmar por el desarrollador]
- **FC:** en % de la FC máx. con decimales, porque intervals.icu no acepta ppm y "% LTHR" usaría la LTHR guardada allí.
- **Potencia:** % del FTP (Coggan, Memoria §8.3) si hay FTP; sin FTP la bici va por FC (Garmin no propone bici sin potenciómetro).
- **Natación:** ritmo absoluto desde el CSS (umbral de natación de intervals.icu); los descansos, sin objetivo.
- Formatos comprobados contra la API el 2026-10-21 (`backend/app/services/workout_text.py`).

## 15. TrainSync frente a Garmin

Objetivo del proyecto: una fórmula propia, determinista y documentada, igual o mejor que las sugerencias de Garmin y su
competencia, sin depender de una IA para proponer sesiones.

| Pieza | Garmin | TrainSync |
|---|---|---|
| Tipos de sesión | Recuperación, base, tempo, umbral, VO2max, anaeróbico, sprint [Verificado: Garmin] | Los mismos (tempo y anaeróbico desde v0.5) + larga, específico de carrera, brick, test y fuerza |
| Elección diaria | Carga, load focus, tiempo de recuperación, sueño, sesiones recientes [Verificado] | Reparto de la carga (§12), TSB, HRV, sueño, RPE, días seguidos y vuelta tras una parada (§3) |
| Hacia una carrera | Sugerencias específicas con fecha; fases no publicadas [Verificado parcial] | Fases, taper por distancia y prioridad A/B/C, todo citado (§8) |
| Objetivos | Carrera por FC (o ritmo); bici solo con potenciómetro [Verificado] | Carrera por ritmo desde tu FC (§13); bici por potencia o FC; natación por CSS (§14) |
| Transparencia | Fórmulas cerradas (Firstbeat) | Cada regla con etiqueta de evidencia y motivos visibles en la app |

No replicable: el EPOC y el Training Effect de Firstbeat (algoritmos cerrados) y los rangos de objetivo de Garmin.

### 15.1 Días del usuario y sesiones hechas

Queja en Garmin Coach y TrainingPeaks (`replica/fixes.md` #4): el plan no respeta los días elegidos y no explica cuándo
da una sesión por hecha.

- **Días.** `fit_days` coloca la plantilla (§8) en los días que el usuario puede entrenar: lo que cae en un día sin
  entreno pasa al día libre más cercano y, si no queda ninguno, desplaza a la sesión menos importante (larga > específico
  > calidad > aeróbico > recuperación). El lunes ya no es descanso fijo si el usuario lo marca. El motivo se ve en la
  sesión ("Viene del domingo: ese día no entrenas"). [Inferencia]
- **Hechas** (planes de salud, `match_day`). Una actividad del reloj (≥ 10 min) cuenta por la sesión del mismo deporte
  ese día; si no la hay, otro cardio cuenta por una sesión de cardio (mismo trabajo aeróbico); la fuerza no sustituye al
  cardio ni al revés. No cuenta si dura menos de la mitad de lo planificado. Lo marcado a mano en una sesión que el reloj
  ya dio por hecha no cuenta dos veces. La adherencia de cada bloque usa este mismo emparejado y la app muestra el motivo de
  cada actividad ("Bici de 45 min a las 08:00 en tu reloj: cuenta por «Aeróbico base»"). [Inferencia]

## 16. Referencias

1. Seiler S. What is best practice for training intensity and duration distribution in endurance athletes? *Int J Sports Physiol Perform.* 2010;5(3):276-291. doi:10.1123/ijspp.5.3.276 — https://pubmed.ncbi.nlm.nih.gov/20861519/
2. Esteve-Lanao J, Foster C, Seiler S, Lucía A. Impact of training intensity distribution on performance in endurance athletes. *J Strength Cond Res.* 2007;21(3):943-949. doi:10.1519/R-19725.1
3. Stöggl T, Sperlich B. Polarized training has greater impact on key endurance variables than threshold, high intensity, or high volume training. *Front Physiol.* 2014;5:33. https://pmc.ncbi.nlm.nih.gov/articles/PMC3912323/
4. Rønnestad BR, et al. Short intervals induce superior training adaptations compared with long intervals in cyclists – an effort-matched approach. *Scand J Med Sci Sports.* 2015;25:143-151.
5. Buchheit M, Laursen PB. High-intensity interval training, solutions to the programming puzzle. Part I: cardiopulmonary emphasis. *Sports Med.* 2013;43:313-338. (Part II: *Sports Med.* 2013;43:927-954.)
6. Bosquet L, Montpetit J, Arvisais D, Mujika I. Effects of tapering on performance: a meta-analysis. *Med Sci Sports Exerc.* 2007;39(8):1358-1365. https://pubmed.ncbi.nlm.nih.gov/17762369/
7. Mujika I, Padilla S. Scientific bases for precompetition tapering strategies. *Med Sci Sports Exerc.* 2003;35(7):1182-1187. https://pubmed.ncbi.nlm.nih.gov/12840640/
8. Rønnestad BR, Mujika I. Optimizing strength training for running and cycling endurance performance: a review. *Scand J Med Sci Sports.* 2014;24(4):603-612. doi:10.1111/sms.12104
9. Blagrove RC, Howatson G, Hayes PR. Effects of strength training on the physiological determinants of middle- and long-distance running performance: a systematic review. *Sports Med.* 2018;48(5):1117-1149. doi:10.1007/s40279-017-0835-7
10. Rønnestad BR, Hansen EA, Raastad T. Effect of heavy strength training on thigh muscle cross-sectional area, performance determinants, and performance in well-trained cyclists. *Eur J Appl Physiol.* 2010;108(5):965-975. doi:10.1007/s00421-009-1307-z
11. Rønnestad BR, et al. (2010b) In-season strength maintenance training increases well-trained cyclists' performance. *Eur J Appl Physiol.* 2010;110(6):1269-1282.
12. Wilson JM, Marin PJ, Rhea MR, et al. Concurrent training: a meta-analysis examining interference of aerobic and resistance exercises. *J Strength Cond Res.* 2012;26(8):2293-2307.
13. Lauersen JB, Bertelsen DM, Andersen LB. The effectiveness of exercise interventions to prevent sports injuries: a systematic review and meta-analysis of randomised controlled trials. *Br J Sports Med.* 2014;48(11):871-877.
14. Foster C, Florhaug JA, Franklin J, et al. A new approach to monitoring exercise training. *J Strength Cond Res.* 2001;15(1):109-115.
15. Vesterinen V, et al. Individual endurance training prescription with heart rate variability. *Med Sci Sports Exerc.* 2016;48(7):1347-1354. https://pubmed.ncbi.nlm.nih.gov/26909534/ — respalda el ajuste diario por HRV (§3): el grupo guiado por HRV hizo menos sesiones duras con mejora similar o mayor.
16. Plews DJ, Laursen PB, Stanley J, Kilding AE, Buchheit M. Training adaptation and heart rate variability in elite endurance athletes: opening the door to effective monitoring. *Sports Med.* 2013;43(9):773-781.
17. Wakayoshi K, et al. A simple method for determining critical speed as swimming fatigue threshold in competitive swimming. *Int J Sports Med.* 1992;13(5):367-371. https://pubmed.ncbi.nlm.nih.gov/1521952/
18. Oliveira PS, Boppre G, Fonseca H. Comparison of polarized versus other types of endurance training intensity distribution on athletes' endurance performance: a systematic review with meta-analysis. *Sports Med.* 2024. doi:10.1007/s40279-024-02034-z — https://pmc.ncbi.nlm.nih.gov/articles/PMC11329428/
19. Rosenblat MA, Perrotta AS, Vicenzino B. Polarized vs. threshold training intensity distribution on endurance sport performance: a systematic review and meta-analysis of randomized controlled trials. *J Strength Cond Res.* 2019;33(12):3491-3500. (Citado por contexto; resultados no leídos.)
20. Editorial: Training intensity, volume and recovery distribution among elite and recreational endurance athletes. *Front Physiol.* 2019. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6537749/ — definición del modelo de 3 zonas.
21. Kiviniemi AM, Hautala AJ, Kinnunen H, Tulppo MP. Endurance training guided individually by daily heart rate variability measurements. *Eur J Appl Physiol.* 2007;101(6):743-751.
22. Javaloyes A, Sarabia JM, Lamberts RP, Moya-Ramon M. Training prescription guided by heart-rate variability in cycling. *Int J Sports Physiol Perform.* 2019;14(1):23-32. doi:10.1123/ijspp.2018-0122
23. Heikura I. Individual adaptation to endurance training guided by heart rate variability. Tesis de máster, Universidad de Jyväskylä, 2015. https://jyx.jyu.fi/handle/123456789/45036
24. Australian Institute of Sport. HRV best-practice guideline. https://www.ais.gov.au/__data/assets/pdf_file/0013/1130260/HRV-BPG_FINAL.pdf
25. Eddens L, et al. The role of intra-session exercise sequence in the interference effect: a systematic review with meta-analysis. *Sports Med.* 2018. doi:10.1007/s40279-017-0784-1
26. Haddad M, et al. Session-RPE method for training load monitoring: validity, ecological usefulness, and influencing factors. *Front Neurosci.* 2017;11:612. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5673663/
27. Fullagar, et al. Sleep and athletic performance: the effects of sleep loss on exercise performance, and physiological and cognitive responses to exercise. *Sports Med.* 2015;45(2):161-186. https://pubmed.ncbi.nlm.nih.gov/25315456/
28. Friel J. Applying the numbers, part 3: Training Stress Balance. TrainingPeaks. https://www.trainingpeaks.com/learn/articles/applying-the-numbers-part-3-training-stress-balance/
29. Friel J. Why ramp rate is an important training metric. TrainingPeaks. https://www.trainingpeaks.com/learn/articles/why-ramp-rate-is-an-important-training-metric/ · Tabla de riesgo por CTL: https://www.trainingpeaks.com/blog/track-triathlon-training-trainingpeaks/
30. Friel J. Quick guide to setting zones. TrainingPeaks. https://www.trainingpeaks.com/learn/articles/joe-friel-s-quick-guide-to-setting-zones/
31. Mujika I, et al. (2000) Taper de 6 días en mediofondistas — resumen en https://coachsci.sdsu.edu/csa/vol101/mujika1.htm
32. Saw AE, Main LC, Gastin PB. Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures: a systematic review. *Br J Sports Med.* 2016;50(5):281-291. https://bjsm.bmj.com/content/50/5/281
33. Foster C. Monitoring training in athletes with reference to overtraining syndrome. *Med Sci Sports Exerc.* 1998;30(7):1164-1168.
34. Rating of perceived exertion as a tool for prescribing and self-regulating interval training: a pilot study. *Biol Sport.* 2015. https://www.termedia.pl/Rating-of-perceived-exertion-as-a-tool-for-prescribing-and-self-regulating-interval-training-a-pilot-study,78,24330,0,1.html
35. A systematic review on markers of functional overreaching in endurance athletes (2021). https://lida.sport-iat.de/ta/Record/4072832
36. Bontemps B, et al. Downhill running: what are the effects and how can we adapt? A narrative review. *Sports Med.* 2020. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7674385/
37. Bontemps B, et al. (2024) Muscle soreness but not neuromuscular fatigue responses following downhill running differ according to the number of exercise bouts. *Eur J Sport Sci.* https://researchonline.ljmu.ac.uk/24976/
38. Lemire M, et al. Physiological factors determining downhill vs uphill running endurance performance. *J Sci Med Sport.* 2021. https://lab3072.unistra.fr/publications/publications-marquantes/physiological-factors-determining-downhill-vs-uphill-running-endurance-performance
39. Casado A, González-Mohíno F, González-Ravé JM, Foster C. Training periodization, methods, intensity distribution, and volume in highly trained and elite distance runners: a systematic review. *Int J Sports Physiol Perform.* 2022. doi:10.1123/ijspp.2021-0435
40. González-Ravé JM, et al. Training intensity distribution, training volume, and periodization models in elite swimmers: a systematic review. *Int J Sports Physiol Perform.* 2021. https://lida.sport-iat.de/ta/Record/4072108
41. Mayo Clinic. Designing your taper to maximize your potential on race day. https://newsnetwork.mayoclinic.org/discussion/how-tapering-maximizes-your-potential-on-marathon-day/
42. Durabilidad en deportes de resistencia: revisión. *Front Sports Act Living.* 2025. https://www.frontiersin.org/journals/sports-and-active-living/articles/10.3389/fspor.2025.1530162/pdf
43. Effects of training and anthropometric factors on marathon and 100 km ultramarathon race performance. *Open Access J Sports Med.* 2015;6. https://pmc.ncbi.nlm.nih.gov/articles/PMC4425319


**Título de la ref. 11:** el resultado de búsqueda confirma el artículo de mantenimiento en *Eur J Appl Physiol* 110(6):1269-82 del mismo grupo, pero no he visto el título ni la lista de autores. [No verificado: título literal y autores]

---

## 17. Control de cambios

| Versión | Fecha | Cambios |
|---|---|---|
| 0.6 | 2026-10-21 | Plantilla ajustada a los días del usuario (`fit_days`) y emparejado de lo hecho con lo planificado con motivo visible (`match_day`) (§15.1). |
| 0.5 | 2026-10-21 | Reparto de la carga como el load focus de Garmin (§12), sesiones tempo y anaeróbicas, vuelta a la base tras 7 días sin entrenar, ritmos de carrera desde la FC con la relación FC-velocidad propia (§13), envío al reloj con una métrica por sesión (§14) y comparación con Garmin (§15). |
| 0.4 | 2026-10-19 | RPE verificado e implementado (§3.3). Todas las pruebas: running por distancia (5K→ultra), trail (§4.1.b), ciclismo de fondo, natación en piscina y aguas abiertas, triatlón/duatlón (§6.4). Taper por distancia (§8.2), topes de larga 5K/ultra (§8.4). Implementados: neuromusculares, tests de onboarding, brick, notas si te sientes mal/bien, pesos por tipo de sesión (§9.1) e intermitentes con fracción de trabajo. Corregido: sin PLAN_START la fase no avanzaba. 12 referencias nuevas. |
| 0.3 | 2026-10-19 | Catálogo implementado en `backend/app/services/session_catalog.py`. Añadida C-OPENER y S-RACE en taper (huecos de la plantilla). En código: S-TECH cubre el slot `recovery`; natación en minutos (~1:45/100 m) mientras no se use el CSS; con poco volumen las series se reducen (mínimo 2, o 1 en bloques ≥ 10′). Pendiente en código: sesiones neuromusculares, tests de campo, bricks, notas "si te sientes mal/bien" y pesos por tipo de sesión (§9.1). |
| 0.2 | 2026-10-19 | Verificación de todas las [Inferencia] contra bibliografía. Nuevas reglas de HRV (§3.1) y TSB (§3.2); zonas de running separadas; techo LTHR − 30 en sesiones fáciles; Z4 contada como moderada; rampa de CTL según TrainingPeaks; TSB de carrera +15/+25; taper B/C; regla de Daniels para la tirada larga; orden fuerza/resistencia (Eddens 2018); descartado 30/15 en carrera; 14 referencias nuevas. Sección 11 reescrita sin revisor externo. |
| 0.1 | 2026-10-19 | Reconstrucción completa: estructuras de entrada, pipeline diario, catálogo de 51 sesiones (18 running, 17 ciclismo, 16 natación), biblioteca de fuerza por fase, plantillas semanales, zona gris, periodización y taper, progresión por TSS, proyección a 14 días, invalidación de caché y referencias verificadas. |
