"""Catálogo de sesiones y biblioteca de fuerza (docs/MOTOR_ENTRENAMIENTO.md §4 y §5).

Solo datos. Bloques con minutes=None absorben la diferencia hasta la duración elegida.
Fuera por ahora: sesiones neuromusculares (strides sueltos, sprints, cuestas cortas),
tests de campo y bricks, que necesitan flujos que aún no existen (onboarding, días multideporte).
"""

ALL = ("base_early", "base", "build", "peak", "taper")
NO_TAPER = ALL[:-1]


def B(block: str, minutes: float | None, zone: str, reps: int = 1, rest: float = 0, detail: str | None = None) -> dict:
    b = {"block": block, "reps": reps, "minutes": minutes, "zone": zone}
    if rest:
        b |= {"rest_minutes": rest, "rest_zone": "Z1"}
    if detail:
        b["detail"] = detail
    return b


def S(id: str, sport: str, category: str, phases: tuple, duration: tuple, blocks: list) -> dict:
    return {"id": id, "sport": sport, "category": category, "phases": phases, "duration": duration, "blocks": blocks}


def easy(zone: str) -> list:
    return [B("calentamiento", 10, "Z1"), B("principal", None, zone), B("vuelta a la calma", 5, "Z1")]


def intervals(reps: int, work: float, zone: str, rest: float, detail: str | None = None, warmup: float = 15) -> list:
    return [B("calentamiento", warmup, "Z2"), B("principal", work, zone, reps, rest, detail), B("vuelta a la calma", None, "Z1")]


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

    # ---- Ciclismo (§4.2)
    S("C-REC", "cycling", "recovery", ALL, (30, 60), [B("principal", None, "Z1")]),
    S("C-Z2", "cycling", "aerobic", ALL, (60, 150), easy("Z2")),
    S("C-Z2-CAD", "cycling", "aerobic", ("base_early", "base"), (60, 90),
      [B("calentamiento", 10, "Z1"), B("cadencia", 2, "Z2", 6, 2, "100–110 rpm"), B("principal", None, "Z2")]),
    S("C-Z2-TORQUE", "cycling", "aerobic", ("base", "build"), (75, 120),
      [B("calentamiento", 15, "Z1"), B("fuerza-resistencia", 5, "Z2", 5, 5, "50–60 rpm en subida, FC en Z2"),
       B("principal", None, "Z2")]),
    S("C-LONG", "cycling", "long", NO_TAPER, (150, 300), easy("Z2")),
    # shortcut: el 30/15 se modela como Z5 continua y sobreestima el TSS (~1/3 del bloque es suave); modelar on/off cuando haya bloques compuestos
    S("C-VO2-3015", "cycling", "vo2max", ("base_early", "base", "build"), (75, 90),
      intervals(3, 13, "Z5", 3, "30″ Z5–Z6 / 15″ suave (Rønnestad 2015)")),
    S("C-VO2-4x5", "cycling", "vo2max", NO_TAPER, (60, 80), intervals(4, 5, "Z5", 2.5)),
    S("C-VO2-5x4", "cycling", "vo2max", ("base", "build", "peak"), (70, 90), intervals(5, 4, "Z5", 4)),
    S("C-VO2-4020", "cycling", "vo2max", ("build", "peak"), (70, 90), intervals(3, 10, "Z5", 5, "40″ Z5–Z6 / 20″ suave")),
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
      intervals(3, 8, "Z4", 2, "distancia de triatlón a ritmo de carrera en 2–3 bloques", warmup=10)),

    # ---- Aguas abiertas: solo build/peak (§4.3)
    S("S-OW", "swimming_open", "race_specific", ("build", "peak"), (30, 60),
      [B("calentamiento", 10, "Z2", detail="orientación cada 6–9 brazadas"), B("principal", 3, "Z4", 4, 2),
       B("vuelta a la calma", None, "Z1")]),
]

# Sesiones donde la Regla 1 permite Z3 (simulacros y progresivos, MOTOR §1.2)
Z3_ALLOWED = {"R-LONG-MP", "R-THR-PROG", "C-RACE-SIM"}

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
