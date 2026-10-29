// Generado una vez desde el catálogo del prototipo; desde el port es la fuente de verdad y se edita aquí (MOTOR §4 y §5).
package io.github.mrclusterman.trainsync.engine

val CATALOG: List<CatalogEntry> = listOf(
    S("R-REC", "running", "recovery", listOf("base_early", "base", "build", "peak", "taper"), 20 to 40,
      listOf(
        B("principal", null, "Z1"),
      )),
    S("R-Z2", "running", "aerobic", listOf("base_early", "base", "build", "peak", "taper"), 40 to 70,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("R-Z2-STR", "running", "aerobic", listOf("base", "build", "peak"), 45 to 60,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("progresivos", 12.0, "Z2", detail = "6\u00d720\u2033 progresivos, 1\u203240\u2033 andando"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("R-LONG", "running", "long", listOf("base_early", "base", "build", "peak"), 75 to 150,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("R-LONG-MP", "running", "race_specific", listOf("peak"), 90 to 150,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("ritmo de carrera", 30.0, "Z3", detail = "\u00faltimos 20\u201340\u2032 a ritmo objetivo (Z3 solo si es el ritmo de carrera)"),
      )),
    S("R-HILL-VO2", "running", "vo2max", listOf("base_early", "base", "build"), 50 to 65,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 1.5, "Z5", reps = 7, rest = 1.5, detail = "en cuesta; recuperaci\u00f3n bajando al trote"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-VO2-3", "running", "vo2max", listOf("base_early", "base", "build", "peak"), 45 to 60,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 3.0, "Z5", reps = 5, rest = 3.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-VO2-2", "running", "vo2max", listOf("base_early", "base"), 45 to 60,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 2.0, "Z5", reps = 8, rest = 2.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-VO2-1K", "running", "vo2max", listOf("build", "peak"), 50 to 65,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 4.0, "Z5", reps = 5, rest = 2.5, detail = "5\u20136\u00d71000 m"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-FARTLEK", "running", "vo2max", listOf("base", "build"), 40 to 55,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 1.0, "Z5", reps = 10, rest = 1.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-THR-CRUISE", "running", "threshold", listOf("build", "peak"), 55 to 70,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 8.0, "Z4", reps = 4, rest = 2.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-THR-2x15", "running", "threshold", listOf("build", "peak"), 60 to 75,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 15.0, "Z4", reps = 2, rest = 4.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-THR-PROG", "running", "threshold", listOf("build"), 50 to 60,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("progresivo", 20.0, "Z2"),
        B("progresivo", 10.0, "Z3"),
        B("progresivo", 10.0, "Z4"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-RACE", "running", "race_specific", listOf("peak", "taper"), 60 to 100,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 15.0, "Z4", reps = 2, rest = 4.0, detail = "a ritmo objetivo: 10K 3\u00d72 km \u00b7 media 2\u00d75 km"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-HILL-NM", "running", "neuromuscular", listOf("base_early", "base", "build"), 40 to 50,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("cuestas m\u00e1ximas", 0.17, "Z6", reps = 8, rest = 2.0, detail = "8\u201310\u00d710\u2033 en cuesta, rec. completa andando"),
        B("principal", null, "Z2"),
      )),
    S("R-TEST-LTHR", "running", "test", listOf("base_early", "base", "build", "peak", "taper"), 50 to 50,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("test", 30.0, "Z4", detail = "30\u2032 al m\u00e1ximo sostenible en solitario; LTHR = FC media de los \u00faltimos 20\u2032"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("T-HILL-VO2", "running", "vo2max", listOf("base_early", "base", "build", "peak"), 50 to 70,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 3.0, "Z5", reps = 6, rest = 3.0, detail = "subida de 3\u2032; bajada al trote controlado"),
        B("vuelta a la calma", null, "Z1"),
      ), events = listOf("trail")),
    S("T-HILL-THR", "running", "threshold", listOf("build", "peak"), 60 to 80,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 10.0, "Z4", reps = 3, rest = 4.0, detail = "subida continua; marcha r\u00e1pida si la pendiente supera ~15 %"),
        B("vuelta a la calma", null, "Z1"),
      ), events = listOf("trail")),
    S("T-LONG", "running", "long", listOf("base_early", "base", "build", "peak"), 90 to 240,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2", detail = "terreno con desnivel; bajadas a ritmo controlado (exposici\u00f3n exc\u00e9ntrica)"),
        B("vuelta a la calma", 5.0, "Z1"),
      ), events = listOf("trail")),
    S("T-DOWNHILL", "running", "neuromuscular", listOf("base", "build"), 45 to 60,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("bajadas", 2.0, "Z2", reps = 6, rest = 3.0, detail = "6\u00d72\u2032 de bajada a ritmo vivo, subida andando (efecto de carga repetida)"),
        B("principal", null, "Z2"),
      ), events = listOf("trail")),
    S("T-RACE-SIM", "running", "race_specific", listOf("peak"), 120 to 240,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2", detail = "recorrido similar a la carrera"),
        B("subidas a ritmo de carrera", 15.0, "Z4", reps = 3, rest = 10.0, detail = "subidas a ritmo de carrera; bajadas t\u00e9cnicas"),
      ), events = listOf("trail")),
    S("C-REC", "cycling", "recovery", listOf("base_early", "base", "build", "peak", "taper"), 30 to 60,
      listOf(
        B("principal", null, "Z1"),
      )),
    S("C-Z2", "cycling", "aerobic", listOf("base_early", "base", "build", "peak", "taper"), 60 to 150,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("C-Z2-CAD", "cycling", "aerobic", listOf("base_early", "base"), 60 to 90,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("cadencia", 2.0, "Z2", reps = 6, rest = 2.0, detail = "100\u2013110 rpm"),
        B("principal", null, "Z2"),
      )),
    S("C-Z2-TORQUE", "cycling", "aerobic", listOf("base", "build"), 75 to 120,
      listOf(
        B("calentamiento", 15.0, "Z1"),
        B("fuerza-resistencia", 5.0, "Z2", reps = 5, rest = 5.0, detail = "50\u201360 rpm en subida, FC en Z2"),
        B("principal", null, "Z2"),
      )),
    S("C-LONG", "cycling", "long", listOf("base_early", "base", "build", "peak"), 150 to 300,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("C-LONG-LATE", "cycling", "long", listOf("build", "peak"), 180 to 300,
      listOf(
        B("calentamiento", 15.0, "Z1"),
        B("principal", null, "Z2"),
        B("esfuerzos al final", 10.0, "Z4", reps = 2, rest = 5.0, detail = "en la \u00faltima hora: durabilidad (rendir con fatiga acumulada)"),
      )),
    S("C-SPRINT", "cycling", "neuromuscular", listOf("base", "build", "peak"), 60 to 90,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("esprints", 0.17, "Z6", reps = 8, rest = 4.0, detail = "6\u20138\u00d710\u2033 a tope"),
        B("principal", null, "Z2"),
      )),
    S("C-TEST", "cycling", "test", listOf("base_early", "base", "build", "peak", "taper"), 55 to 55,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("test", 30.0, "Z4", detail = "30\u2032 al m\u00e1ximo sostenible; LTHR = FC media de los \u00faltimos 20\u2032"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-BRICK", "cycling", "brick", listOf("build", "peak"), 75 to 150,
      listOf(
        B("calentamiento", 15.0, "Z1"),
        B("bici", null, "Z2"),
        B("bici a ritmo de carrera", 20.0, "Z4"),
        B("carrera a pie", 15.0, "Z2", detail = "transici\u00f3n inmediata; usa tus zonas de carrera a pie"),
      )),
    S("C-VO2-3015", "cycling", "vo2max", listOf("base_early", "base", "build"), 75 to 90,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 13.0, "Z5", reps = 3, rest = 3.0, detail = "30\u2033 Z5\u2013Z6 / 15\u2033 suave (R\u00f8nnestad 2015)", duty = 0.67),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-VO2-4x5", "cycling", "vo2max", listOf("base_early", "base", "build", "peak"), 60 to 80,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 5.0, "Z5", reps = 4, rest = 2.5),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-VO2-5x4", "cycling", "vo2max", listOf("base", "build", "peak"), 70 to 90,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 4.0, "Z5", reps = 5, rest = 4.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-VO2-4020", "cycling", "vo2max", listOf("build", "peak"), 70 to 90,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 10.0, "Z5", reps = 3, rest = 5.0, detail = "40\u2033 Z5\u2013Z6 / 20\u2033 suave", duty = 0.67),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-THR-3x12", "cycling", "threshold", listOf("build", "peak"), 75 to 100,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 12.0, "Z4", reps = 3, rest = 4.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-THR-2x20", "cycling", "threshold", listOf("build", "peak"), 80 to 110,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 20.0, "Z4", reps = 2, rest = 5.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-THR-OU", "cycling", "threshold", listOf("build", "peak"), 75 to 95,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 12.0, "Z4", reps = 3, rest = 5.0, detail = "4\u00d7(2\u2032 Z4 / 1\u2032 Z5)"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-CLIMB", "cycling", "threshold", listOf("build", "peak"), 90 to 180,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 12.0, "Z4", reps = 3, rest = 8.0, detail = "subidas de 8\u201320\u2032; bajada de recuperaci\u00f3n"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-RACE-SIM", "cycling", "race_specific", listOf("peak"), 180 to 300,
      listOf(
        B("calentamiento", 15.0, "Z1"),
        B("principal", null, "Z2"),
        B("ritmo de evento", 25.0, "Z3", reps = 2, rest = 10.0, detail = "Z3 solo si es el ritmo del evento (gran fondo)"),
      )),
    S("C-OPENER", "cycling", "race_specific", listOf("taper"), 45 to 75,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 3.0, "Z4", reps = 3, rest = 3.0, detail = "activaci\u00f3n a ritmo de carrera"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-REC", "swimming_pool", "recovery", listOf("base_early", "base", "build", "peak", "taper"), 20 to 35,
      listOf(
        B("principal", null, "Z1", detail = "1.000\u20131.500 m + t\u00e9cnica suave"),
      )),
    S("S-TECH", "swimming_pool", "recovery", listOf("base_early", "base", "build", "peak", "taper"), 30 to 40,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("t\u00e9cnica", null, "Z2", detail = "brazada de alcance (catch-up), 6 patadas por brazada, remadas de manos (sculling), con pull buoy"),
      )),
    S("S-AERO", "swimming_pool", "aerobic", listOf("base_early", "base", "build", "peak", "taper"), 40 to 60,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("principal", 7.0, "Z2", reps = 5, rest = 0.33, detail = "4\u20136\u00d7400, desc. 20\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-PULL", "swimming_pool", "aerobic", listOf("base", "build"), 40 to 50,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("principal", 3.5, "Z2", reps = 6, rest = 0.33, detail = "6\u00d7200 con pull buoy (flotador entre las piernas) y palas"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-KICK", "swimming_pool", "aerobic", listOf("base"), 35 to 45,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("patada", 1.25, "Z2", reps = 8, rest = 0.25, detail = "8\u00d750 patada"),
        B("principal", null, "Z2"),
      )),
    S("S-LONG", "swimming_pool", "long", listOf("base_early", "base", "build", "peak"), 45 to 70,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("S-CSS-100", "swimming_pool", "threshold", listOf("base", "build", "peak"), 40 to 55,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 1.75, "Z4", reps = 10, rest = 0.25, detail = "8\u201312\u00d7100 a CSS, desc. 10\u201315\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-CSS-200", "swimming_pool", "threshold", listOf("build", "peak"), 45 to 55,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 3.5, "Z4", reps = 5, rest = 0.33, detail = "5\u00d7200, desc. 20\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-THR-400", "swimming_pool", "threshold", listOf("build", "peak"), 45 to 60,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 7.0, "Z4", reps = 3, rest = 0.5, detail = "3\u00d7400, desc. 30\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-PYRAMID", "swimming_pool", "threshold", listOf("build"), 40 to 50,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 15.0, "Z4", detail = "100-200-300-200-100, desc. 20\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-VO2-50", "swimming_pool", "vo2max", listOf("base_early", "base", "build"), 35 to 50,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 0.8, "Z5", reps = 16, rest = 0.33, detail = "16\u00d750, desc. 15\u201320\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-VO2-100", "swimming_pool", "vo2max", listOf("base", "build", "peak"), 40 to 50,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 1.6, "Z5", reps = 8, rest = 0.5, detail = "8\u00d7100, desc. 30\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-RACE", "swimming_pool", "race_specific", listOf("peak", "taper"), 30 to 60,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 8.0, "Z4", reps = 3, rest = 2.0, detail = "distancia de carrera a ritmo objetivo en 2\u20133 bloques"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-SPRINT", "swimming_pool", "neuromuscular", listOf("base", "build"), 30 to 45,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("esprints", 0.4, "Z6", reps = 8, rest = 0.75, detail = "8\u00d725 m\u00e1ximo, desc. 45\u2033"),
        B("principal", null, "Z2"),
      )),
    S("S-TEST-CSS", "swimming_pool", "test", listOf("base_early", "base", "build", "peak", "taper"), 40 to 40,
      listOf(
        B("calentamiento", 10.0, "Z1", detail = "400 m suave + t\u00e9cnica"),
        B("test 400", 7.0, "Z5", detail = "400 m al m\u00e1ximo"),
        B("recuperaci\u00f3n", 8.0, "Z1"),
        B("test 200", 3.5, "Z5", detail = "200 m al m\u00e1ximo; CSS (s/100 m) = (T400 \u2212 T200) / 2"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-OW-LONG", "swimming_open", "long", listOf("build", "peak"), 40 to 90,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2", detail = "continuo; orientaci\u00f3n cada 6\u20139 brazadas, practicar ir a rebufo"),
      )),
    S("S-OW", "swimming_open", "race_specific", listOf("build", "peak"), 30 to 60,
      listOf(
        B("calentamiento", 10.0, "Z2", detail = "orientaci\u00f3n cada 6\u20139 brazadas"),
        B("principal", 3.0, "Z4", reps = 4, rest = 2.0),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("W-EASY", "walking", "recovery", listOf("base_early", "base", "build", "peak", "taper"), 20 to 60,
      listOf(
        B("principal", null, "Z1", detail = "paseo a ritmo c\u00f3modo"),
      )),
    S("W-BRISK", "walking", "aerobic", listOf("base_early", "base", "build", "peak", "taper"), 30 to 90,
      listOf(
        B("calentamiento", 5.0, "Z1"),
        B("principal", null, "Z2", detail = "paso vivo: puedes hablar pero no cantar"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("W-LONG", "walking", "long", listOf("base_early", "base", "build", "peak", "taper"), 60 to 180,
      listOf(
        B("calentamiento", 10.0, "Z1"),
        B("principal", null, "Z2"),
        B("vuelta a la calma", 5.0, "Z1"),
      )),
    S("W-HILLS", "walking", "threshold", listOf("base_early", "base", "build", "peak", "taper"), 35 to 60,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 3.0, "Z4", reps = 6, rest = 2.0, detail = "subida a paso r\u00e1pido (solo frases cortas); bajada tranquila"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-TEMPO", "running", "tempo", listOf("build", "peak"), 50 to 75,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 10.0, "Z3", reps = 3, rest = 2.0, detail = "a ritmo de marat\u00f3n"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("R-ANA", "running", "anaerobic", listOf("base", "build", "peak"), 40 to 55,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 0.75, "Z6", reps = 8, rest = 2.5, detail = "8\u00d745\u2033 muy fuerte; recuperaci\u00f3n al trote"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-TEMPO", "cycling", "tempo", listOf("build", "peak"), 75 to 105,
      listOf(
        B("calentamiento", 15.0, "Z2"),
        B("principal", 15.0, "Z3", reps = 3, rest = 4.0, detail = "a ritmo de prueba larga"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("C-ANA", "cycling", "anaerobic", listOf("base", "build", "peak"), 55 to 75,
      listOf(
        B("calentamiento", 20.0, "Z2"),
        B("principal", 0.75, "Z6", reps = 8, rest = 3.0, detail = "8\u00d745\u2033 muy fuerte; recuperaci\u00f3n suave"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-TEMPO", "swimming_pool", "tempo", listOf("build", "peak"), 40 to 55,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 5.0, "Z3", reps = 4, rest = 0.5, detail = "4\u00d7300, desc. 30\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
    S("S-ANA", "swimming_pool", "anaerobic", listOf("base", "build", "peak"), 30 to 45,
      listOf(
        B("calentamiento", 10.0, "Z2"),
        B("principal", 0.8, "Z6", reps = 8, rest = 1.5, detail = "8\u00d750 muy fuerte, desc. 1\u203230\u2033"),
        B("vuelta a la calma", null, "Z1"),
      )),
)

val Z3_ALLOWED: Set<String> = setOf("C-RACE-SIM", "C-TEMPO", "R-LONG-MP", "R-TEMPO", "R-THR-PROG", "S-TEMPO")

val NOTES: Map<String, Pair<String, String>> = mapOf(
    "recovery" to ("Si te sientes mal: acorta o descansa; hoy no suma forzar." to "Si te sientes bien: no alargues; recuperar es el objetivo."),
    "aerobic" to ("Si te sientes mal: qu\u00e9date en Z1 y acorta un tercio." to "Si te sientes bien: respeta el techo de FC; no subas a zona gris."),
    "long" to ("Si te sientes mal: corta en el punto medio y vuelve suave." to "Si te sientes bien: mant\u00e9n la zona; la duraci\u00f3n es el est\u00edmulo."),
    "neuromuscular" to ("Si te sientes mal: quita los esfuerzos y haz solo el rodaje." to "Si te sientes bien: no a\u00f1adas repeticiones; calidad m\u00e1xima en cada una."),
    "vo2max" to ("Si la FC no sube a Z5 en la 2.\u00aa serie o te notas vac\u00edo: termina en Z2." to "Si te sientes bien: no a\u00f1adas series; la calidad est\u00e1 en la ejecuci\u00f3n."),
    "threshold" to ("Si no sostienes la zona: quita la \u00faltima serie." to "Si te sientes bien: mant\u00e9n la zona, no la subas a Z5."),
    "race_specific" to ("Si no aguantas el ritmo objetivo: pasa a Z2 y vuelve a intentarlo la semana siguiente." to "Si te sientes bien: aguanta el ritmo exacto; no lo superes."),
    "brick" to ("Si te sientes mal: haz solo la bici en Z2." to "Si te sientes bien: c\u00e9ntrate en una transici\u00f3n r\u00e1pida, no en ir m\u00e1s fuerte."),
    "tempo" to ("Si no sostienes el ritmo de marat\u00f3n: haz el resto en Z2." to "Si te sientes bien: ritmo exacto de carrera, no m\u00e1s r\u00e1pido."),
    "anaerobic" to ("Si la 3.\u00aa serie ya sale lenta: termina en Z1." to "Si te sientes bien: no a\u00f1adas series; descansa completo entre ellas."),
    "test" to ("Si no est\u00e1s descansado: aplaza el test; un test fatigado da zonas err\u00f3neas." to "Hazlo en solitario y en terreno constante."),
)

/** Fase → (dosis, RPE planificado, minutos). */
val STRENGTH_DOSE: Map<String, Triple<String, Int, Int>> = mapOf(
    "base_early" to Triple("2\u20133 \u00d7 12\u201315, carga moderada (adaptaci\u00f3n)", 6, 40),
    "base" to Triple("3\u20134 \u00d7 4\u20138 RM (fuerza m\u00e1xima)", 7, 50),
    "build" to Triple("3 \u00d7 4\u20136 RM + 3 \u00d7 5\u20138 saltos", 7, 50),
    "peak" to Triple("2\u20133 \u00d7 4\u20136 RM (mantenimiento)", 6, 35),
)

val LOWER: Map<String, List<String>> = mapOf(
    "gym" to listOf("Sentadilla con barra", "Peso muerto rumano", "Zancada b\u00falgara con mancuernas", "Elevaci\u00f3n de gemelo de pie y sentado", "Empuje de cadera con barra (hip thrust)"),
    "bodyweight" to listOf("Sentadilla b\u00falgara (progresi\u00f3n a sentadilla a una pierna)", "Peso muerto rumano a una pierna", "Subida a caj\u00f3n alto", "Gemelo a una pierna en escal\u00f3n", "Puente de gl\u00fateo a una pierna"),
)

val PLYO: Map<String, List<String>> = mapOf(
    "gym" to listOf("Saltos al caj\u00f3n", "Saltos de ca\u00edda desde poca altura (drop jumps)"),
    "bodyweight" to listOf("Saltos de tobillo seguidos (pogo)", "Saltos de rana"),
)

val UPPER: Map<String, List<String>> = mapOf(
    "gym" to listOf("Jal\u00f3n al pecho o dominadas lastradas", "Remo con mancuerna", "Press de banca con mancuernas", "Tir\u00f3n a la cara con polea (face pull)"),
    "bodyweight" to listOf("Dominadas o negativas", "Remo invertido", "Flexiones", "Rotaci\u00f3n externa con goma"),
)

val CORE: List<String> = listOf("Plancha frontal", "Plancha lateral", "Bicho muerto (dead bug)", "Press Pallof antirrotaci\u00f3n", "Plancha de Copenhague")
