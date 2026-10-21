"""Catálogo de sesiones y biblioteca de fuerza (docs/MOTOR_ENTRENAMIENTO.md §4 y §5).

Solo datos. Bloques con minutes=None absorben la diferencia hasta la duración elegida.
"events" limita una sesión a un tipo de prueba (p. ej. trail); "duty" es la fracción de trabajo
de un bloque intermitente (30/15 = 0,67) para no sobreestimar su TSS.
"""

ALL = ("base_early", "base", "build", "peak", "taper")
NO_TAPER = ALL[:-1]


def B(block: str, minutes: float | None, zone: str, reps: int = 1, rest: float = 0, detail: str | None = None,
      duty: float | None = None) -> dict:
    b = {"block": block, "reps": reps, "minutes": minutes, "zone": zone}
    if rest:
        b |= {"rest_minutes": rest, "rest_zone": "Z1"}
    if detail:
        b["detail"] = detail
    if duty:
        b["duty"] = duty
    return b


def S(id: str, sport: str, category: str, phases: tuple, duration: tuple, blocks: list, events: tuple = ()) -> dict:
    s = {"id": id, "sport": sport, "category": category, "phases": phases, "duration": duration, "blocks": blocks}
    return s | {"events": events} if events else s


def easy(zone: str) -> list:
    return [B("calentamiento", 10, "Z1"), B("principal", None, zone), B("vuelta a la calma", 5, "Z1")]


def intervals(reps: int, work: float, zone: str, rest: float, detail: str | None = None, warmup: float = 15,
              duty: float | None = None) -> list:
    return [B("calentamiento", warmup, "Z2"), B("principal", work, zone, reps, rest, detail, duty),
            B("vuelta a la calma", None, "Z1")]


# shortcut: natación en minutos con ~1:45/100 m sin CSS; pasar a metros cuando el plan use el CSS del usuario
SWIM_WARMUP = "400 m suave + técnica"

CATALOG = [
    # ---- Running (§4.1)
    S("R-REC", "running", "recovery", ALL, (20, 40), [B("principal", None, "Z1")]),
    S("R-Z2", "running", "aerobic", ALL, (40, 70), easy("Z2")),
    S("R-Z2-STR", "running", "aerobic", ("base", "build", "peak"), (45, 60),
      [B("calentamiento", 10, "Z1"), B("principal", None, "Z2"),
       B("progresivos", 12, "Z2", detail="6×20″ progresivos, 1′40″ andando"), B("vuelta a la calma", 5, "Z1")]),
    S("R-LONG", "running", "long", NO_TAPER, (75, 150), easy("Z2")),
    S("R-LONG-MP", "running", "race_specific", ("peak",), (90, 150),
      [B("calentamiento", 10, "Z1"), B("principal", None, "Z2"),
       B("ritmo de carrera", 30, "Z3", detail="últimos 20–40′ a ritmo objetivo (Z3 solo si es el ritmo de carrera)")]),
    S("R-HILL-VO2", "running", "vo2max", ("base_early", "base", "build"), (50, 65),
      intervals(7, 1.5, "Z5", 1.5, "en cuesta; recuperación bajando al trote")),
    S("R-VO2-3", "running", "vo2max", NO_TAPER, (45, 60), intervals(5, 3, "Z5", 3)),
    S("R-VO2-2", "running", "vo2max", ("base_early", "base"), (45, 60), intervals(8, 2, "Z5", 2)),
    S("R-VO2-1K", "running", "vo2max", ("build", "peak"), (50, 65), intervals(5, 4, "Z5", 2.5, "5–6×1000 m")),
    S("R-FARTLEK", "running", "vo2max", ("base", "build"), (40, 55), intervals(10, 1, "Z5", 1)),
    S("R-THR-CRUISE", "running", "threshold", ("build", "peak"), (55, 70), intervals(4, 8, "Z4", 2)),
    S("R-THR-2x15", "running", "threshold", ("build", "peak"), (60, 75), intervals(2, 15, "Z4", 4)),
    S("R-THR-PROG", "running", "threshold", ("build",), (50, 60),
      [B("calentamiento", 10, "Z1"), B("progresivo", 20, "Z2"), B("progresivo", 10, "Z3"),
       B("progresivo", 10, "Z4"), B("vuelta a la calma", None, "Z1")]),
    S("R-RACE", "running", "race_specific", ("peak", "taper"), (60, 100),
      intervals(2, 15, "Z4", 4, "a ritmo objetivo: 10K 3×2 km · media 2×5 km")),
    S("R-HILL-NM", "running", "neuromuscular", ("base_early", "base", "build"), (40, 50),
      [B("calentamiento", 15, "Z2"), B("cuestas máximas", 0.17, "Z6", 8, 2, "8–10×10″ en cuesta, rec. completa andando"),
       B("principal", None, "Z2")]),
    S("R-TEST-LTHR", "running", "test", ALL, (50, 50),
      [B("calentamiento", 15, "Z2"), B("test", 30, "Z4", detail="30′ al máximo sostenible en solitario; LTHR = FC media de los últimos 20′"),
       B("vuelta a la calma", None, "Z1")]),

    # ---- Trail (§4.4): subidas, bajadas controladas y largas por tiempo en desnivel
    S("T-HILL-VO2", "running", "vo2max", NO_TAPER, (50, 70),
      intervals(6, 3, "Z5", 3, "subida de 3′; bajada al trote controlado"), events=("trail",)),
    S("T-HILL-THR", "running", "threshold", ("build", "peak"), (60, 80),
      intervals(3, 10, "Z4", 4, "subida continua; marcha rápida si la pendiente supera ~15 %"), events=("trail",)),
    S("T-LONG", "running", "long", NO_TAPER, (90, 240),
      [B("calentamiento", 10, "Z1"), B("principal", None, "Z2", detail="terreno con desnivel; bajadas a ritmo controlado (exposición excéntrica)"),
       B("vuelta a la calma", 5, "Z1")], events=("trail",)),
    S("T-DOWNHILL", "running", "neuromuscular", ("base", "build"), (45, 60),
      [B("calentamiento", 15, "Z2"), B("bajadas", 2, "Z2", 6, 3, "6×2′ de bajada a ritmo vivo, subida andando (efecto de carga repetida)"),
       B("principal", None, "Z2")], events=("trail",)),
    S("T-RACE-SIM", "running", "race_specific", ("peak",), (120, 240),
      [B("calentamiento", 10, "Z1"), B("principal", None, "Z2", detail="recorrido similar a la carrera"),
       B("subidas a ritmo de carrera", 15, "Z4", 3, 10, "subidas a ritmo de carrera; bajadas técnicas")], events=("trail",)),

    # ---- Ciclismo (§4.2)
    S("C-REC", "cycling", "recovery", ALL, (30, 60), [B("principal", None, "Z1")]),
    S("C-Z2", "cycling", "aerobic", ALL, (60, 150), easy("Z2")),
    S("C-Z2-CAD", "cycling", "aerobic", ("base_early", "base"), (60, 90),
      [B("calentamiento", 10, "Z1"), B("cadencia", 2, "Z2", 6, 2, "100–110 rpm"), B("principal", None, "Z2")]),
    S("C-Z2-TORQUE", "cycling", "aerobic", ("base", "build"), (75, 120),
      [B("calentamiento", 15, "Z1"), B("fuerza-resistencia", 5, "Z2", 5, 5, "50–60 rpm en subida, FC en Z2"),
       B("principal", None, "Z2")]),
    S("C-LONG", "cycling", "long", NO_TAPER, (150, 300), easy("Z2")),
    S("C-LONG-LATE", "cycling", "long", ("build", "peak"), (180, 300),
      [B("calentamiento", 15, "Z1"), B("principal", None, "Z2"),
       B("esfuerzos al final", 10, "Z4", 2, 5, "en la última hora: durabilidad (rendir con fatiga acumulada)")]),
    S("C-SPRINT", "cycling", "neuromuscular", ("base", "build", "peak"), (60, 90),
      [B("calentamiento", 15, "Z2"), B("sprints", 0.17, "Z6", 8, 4, "6–8×10″ sprint"), B("principal", None, "Z2")]),
    S("C-TEST", "cycling", "test", ALL, (55, 55),
      [B("calentamiento", 15, "Z2"), B("test", 30, "Z4", detail="30′ al máximo sostenible; LTHR = FC media de los últimos 20′"),
       B("vuelta a la calma", None, "Z1")]),
    S("C-BRICK", "cycling", "brick", ("build", "peak"), (75, 150),
      [B("calentamiento", 15, "Z1"), B("bici", None, "Z2"), B("bici a ritmo de carrera", 20, "Z4"),
       B("carrera a pie", 15, "Z2", detail="transición inmediata; usa tus zonas de running")]),
    S("C-VO2-3015", "cycling", "vo2max", ("base_early", "base", "build"), (75, 90),
      intervals(3, 13, "Z5", 3, "30″ Z5–Z6 / 15″ suave (Rønnestad 2015)", duty=0.67)),
    S("C-VO2-4x5", "cycling", "vo2max", NO_TAPER, (60, 80), intervals(4, 5, "Z5", 2.5)),
    S("C-VO2-5x4", "cycling", "vo2max", ("base", "build", "peak"), (70, 90), intervals(5, 4, "Z5", 4)),
    S("C-VO2-4020", "cycling", "vo2max", ("build", "peak"), (70, 90), intervals(3, 10, "Z5", 5, "40″ Z5–Z6 / 20″ suave", duty=0.67)),
    S("C-THR-3x12", "cycling", "threshold", ("build", "peak"), (75, 100), intervals(3, 12, "Z4", 4)),
    S("C-THR-2x20", "cycling", "threshold", ("build", "peak"), (80, 110), intervals(2, 20, "Z4", 5)),
    S("C-THR-OU", "cycling", "threshold", ("build", "peak"), (75, 95), intervals(3, 12, "Z4", 5, "4×(2′ Z4 / 1′ Z5)")),
    S("C-CLIMB", "cycling", "threshold", ("build", "peak"), (90, 180),
      intervals(3, 12, "Z4", 8, "subidas de 8–20′; bajada de recuperación")),
    S("C-RACE-SIM", "cycling", "race_specific", ("peak",), (180, 300),
      [B("calentamiento", 15, "Z1"), B("principal", None, "Z2"),
       B("ritmo de evento", 25, "Z3", 2, 10, "Z3 solo si es el ritmo del evento (gran fondo)")]),
    S("C-OPENER", "cycling", "race_specific", ("taper",), (45, 75), intervals(3, 3, "Z4", 3, "activación a ritmo de carrera")),

    # ---- Natación en piscina (§4.3)
    S("S-REC", "swimming_pool", "recovery", ALL, (20, 35), [B("principal", None, "Z1", detail="1.000–1.500 m + técnica suave")]),
    S("S-TECH", "swimming_pool", "recovery", ALL, (30, 40),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP),
       B("técnica", None, "Z2", detail="catch-up, 6 patadas/brazada, sculling, pull buoy")]),
    S("S-AERO", "swimming_pool", "aerobic", ALL, (40, 60),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP), B("principal", 7, "Z2", 5, 0.33, "4–6×400, desc. 20″"),
       B("vuelta a la calma", None, "Z1")]),
    S("S-PULL", "swimming_pool", "aerobic", ("base", "build"), (40, 50),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP), B("principal", 3.5, "Z2", 6, 0.33, "6×200 pull buoy + palas"),
       B("vuelta a la calma", None, "Z1")]),
    S("S-KICK", "swimming_pool", "aerobic", ("base",), (35, 45),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP), B("patada", 1.25, "Z2", 8, 0.25, "8×50 patada"),
       B("principal", None, "Z2")]),
    S("S-LONG", "swimming_pool", "long", NO_TAPER, (45, 70), easy("Z2")),
    S("S-CSS-100", "swimming_pool", "threshold", ("base", "build", "peak"), (40, 55),
      intervals(10, 1.75, "Z4", 0.25, "8–12×100 a CSS, desc. 10–15″", warmup=10)),
    S("S-CSS-200", "swimming_pool", "threshold", ("build", "peak"), (45, 55),
      intervals(5, 3.5, "Z4", 0.33, "5×200, desc. 20″", warmup=10)),
    S("S-THR-400", "swimming_pool", "threshold", ("build", "peak"), (45, 60),
      intervals(3, 7, "Z4", 0.5, "3×400, desc. 30″", warmup=10)),
    S("S-PYRAMID", "swimming_pool", "threshold", ("build",), (40, 50),
      intervals(1, 15, "Z4", 0, "100-200-300-200-100, desc. 20″", warmup=10)),
    S("S-VO2-50", "swimming_pool", "vo2max", ("base_early", "base", "build"), (35, 50),
      intervals(16, 0.8, "Z5", 0.33, "16×50, desc. 15–20″", warmup=10)),
    S("S-VO2-100", "swimming_pool", "vo2max", ("base", "build", "peak"), (40, 50),
      intervals(8, 1.6, "Z5", 0.5, "8×100, desc. 30″", warmup=10)),
    S("S-RACE", "swimming_pool", "race_specific", ("peak", "taper"), (30, 60),
      intervals(3, 8, "Z4", 2, "distancia de carrera a ritmo objetivo en 2–3 bloques", warmup=10)),
    S("S-SPRINT", "swimming_pool", "neuromuscular", ("base", "build"), (30, 45),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP), B("sprints", 0.4, "Z6", 8, 0.75, "8×25 máximo, desc. 45″"),
       B("principal", None, "Z2")]),
    S("S-TEST-CSS", "swimming_pool", "test", ALL, (40, 40),
      [B("calentamiento", 10, "Z1", detail=SWIM_WARMUP), B("test 400", 7, "Z5", detail="400 m al máximo"),
       B("recuperación", 8, "Z1"), B("test 200", 3.5, "Z5", detail="200 m al máximo; CSS (s/100 m) = (T400 − T200) / 2"),
       B("vuelta a la calma", None, "Z1")]),

    # ---- Aguas abiertas: solo build/peak (§4.3)
    S("S-OW-LONG", "swimming_open", "long", ("build", "peak"), (40, 90),
      [B("calentamiento", 10, "Z1"), B("principal", None, "Z2", detail="continuo; orientación cada 6–9 brazadas, practicar ir a rebufo")]),
    S("S-OW", "swimming_open", "race_specific", ("build", "peak"), (30, 60),
      [B("calentamiento", 10, "Z2", detail="orientación cada 6–9 brazadas"), B("principal", 3, "Z4", 4, 2),
       B("vuelta a la calma", None, "Z1")]),

    # ---- Caminar: la actividad aeróbica de entrada para principiantes y mayores (docs/proyecto/DISENO.md §2 y §7)
    # Moderado = paso vivo, se puede hablar pero no cantar (prueba del habla) [Práctica, coherente con OMS 2020]
    S("W-EASY", "walking", "recovery", ALL, (20, 60), [B("principal", None, "Z1", detail="paseo a ritmo cómodo")]),
    S("W-BRISK", "walking", "aerobic", ALL, (30, 90),
      [B("calentamiento", 5, "Z1"), B("principal", None, "Z2", detail="paso vivo: puedes hablar pero no cantar"),
       B("vuelta a la calma", 5, "Z1")]),
    S("W-LONG", "walking", "long", ALL, (60, 180), easy("Z2")),
    S("W-HILLS", "walking", "threshold", ALL, (35, 60),
      intervals(6, 3, "Z4", 2, "subida a paso rápido (solo frases cortas); bajada tranquila", warmup=10)),

    # ---- Tempo y anaeróbico: los dos tipos de Garmin que faltaban (docs/MOTOR_ENTRENAMIENTO.md §12 y §15)
    # Tempo = ritmo de maratón, continuo o en bloques suaves; solo para media/maratón (polarizado: Z3 no es objetivo
    # salvo cuando es el ritmo de carrera). Anaeróbico = 40–60″ por encima del VO2max con recuperación larga.
    # [Práctica: Garmin, "Types of daily suggested workouts for runners"; series y descansos: Inferencia]
    S("R-TEMPO", "running", "tempo", ("build", "peak"), (50, 75), intervals(3, 10, "Z3", 2, "a ritmo de maratón")),
    S("R-ANA", "running", "anaerobic", ("base", "build", "peak"), (40, 55),
      intervals(8, 0.75, "Z6", 2.5, "8×45″ muy fuerte; recuperación al trote", warmup=15)),
    S("C-TEMPO", "cycling", "tempo", ("build", "peak"), (75, 105), intervals(3, 15, "Z3", 4, "a ritmo de prueba larga")),
    S("C-ANA", "cycling", "anaerobic", ("base", "build", "peak"), (55, 75),
      intervals(8, 0.75, "Z6", 3, "8×45″ muy fuerte; recuperación suave", warmup=20)),
    S("S-TEMPO", "swimming_pool", "tempo", ("build", "peak"), (40, 55), intervals(4, 5, "Z3", 0.5, "4×300, desc. 30″", warmup=10)),
    S("S-ANA", "swimming_pool", "anaerobic", ("base", "build", "peak"), (30, 45),
      intervals(8, 0.8, "Z6", 1.5, "8×50 muy fuerte, desc. 1′30″", warmup=10)),
]

# Sesiones donde la Regla 1 permite Z3 (simulacros y progresivos, MOTOR §1.2)
Z3_ALLOWED = {"R-LONG-MP", "R-THR-PROG", "C-RACE-SIM", "R-TEMPO", "C-TEMPO", "S-TEMPO"}  # tempo = ritmo de carrera larga

# Notas de adaptación por categoría (diseño: "si te sientes mal / bien"). [Práctica]
NOTES = {
    "recovery": ("Si te sientes mal: acorta o descansa; hoy no suma forzar.", "Si te sientes bien: no alargues; recuperar es el objetivo."),
    "aerobic": ("Si te sientes mal: quédate en Z1 y acorta un tercio.", "Si te sientes bien: respeta el techo de FC; no subas a zona gris."),
    "long": ("Si te sientes mal: corta en el punto medio y vuelve suave.", "Si te sientes bien: mantén la zona; la duración es el estímulo."),
    "neuromuscular": ("Si te sientes mal: quita los esfuerzos y haz solo el rodaje.", "Si te sientes bien: no añadas repeticiones; calidad máxima en cada una."),
    "vo2max": ("Si la FC no sube a Z5 en la 2.ª serie o te notas vacío: termina en Z2.", "Si te sientes bien: no añadas series; la calidad está en la ejecución."),
    "threshold": ("Si no sostienes la zona: quita la última serie.", "Si te sientes bien: mantén la zona, no la subas a Z5."),
    "race_specific": ("Si no aguantas el ritmo objetivo: pasa a Z2 y vuelve a intentarlo la semana siguiente.", "Si te sientes bien: aguanta el ritmo exacto; no lo superes."),
    "brick": ("Si te sientes mal: haz solo la bici en Z2.", "Si te sientes bien: céntrate en una transición rápida, no en ir más fuerte."),
    "tempo": ("Si no sostienes el ritmo de maratón: haz el resto en Z2.", "Si te sientes bien: ritmo exacto de carrera, no más rápido."),
    "anaerobic": ("Si la 3.ª serie ya sale lenta: termina en Z1.", "Si te sientes bien: no añadas series; descansa completo entre ellas."),
    "test": ("Si no estás descansado: aplaza el test; un test fatigado da zonas erróneas.", "Hazlo en solitario y en terreno constante."),
}

# ---- Fuerza (§5.1, §5.2): (dosis, RPE planificado, minutos [Inferencia])
STRENGTH_DOSE = {
    "base_early": ("2–3 × 12–15, carga moderada (adaptación)", 6, 40),
    "base": ("3–4 × 4–8 RM (fuerza máxima)", 7, 50),
    "build": ("3 × 4–6 RM + 3 × 5–8 saltos", 7, 50),
    "peak": ("2–3 × 4–6 RM (mantenimiento)", 6, 35),
}
LOWER = {
    "gym": ["Sentadilla con barra", "Peso muerto rumano", "Zancada búlgara con mancuernas",
            "Elevación de gemelo de pie y sentado", "Hip thrust con barra"],
    "bodyweight": ["Sentadilla búlgara (progresión a pistol)", "Peso muerto rumano a una pierna",
                   "Step-up alto", "Gemelo a una pierna en escalón", "Puente de glúteo a una pierna"],
}
PLYO = {"gym": ["Saltos al cajón", "Drop jumps bajos"], "bodyweight": ["Pogo jumps", "Saltos de rana"]}
UPPER = {
    "gym": ["Jalón al pecho o dominadas lastradas", "Remo con mancuerna", "Press de banca con mancuernas",
            "Face pull"],
    "bodyweight": ["Dominadas o negativas", "Remo invertido", "Flexiones", "Rotación externa con goma"],
}
CORE = ["Plancha frontal", "Plancha lateral", "Dead bug", "Pallof press", "Plancha de Copenhague"]
