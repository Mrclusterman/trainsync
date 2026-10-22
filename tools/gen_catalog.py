"""Genera shared/.../Catalog.kt desde el catálogo del prototipo (prototype/backend/app/services/session_catalog.py).

Se usó una vez al portar el motor a Kotlin; desde entonces Catalog.kt es la fuente de verdad. Ejecutar desde
prototype/backend: `python ../../tools/gen_catalog.py > ../../shared/src/commonMain/kotlin/.../Catalog.kt`.
"""
import json
from app.services import session_catalog as sc

q = json.dumps  # cadenas Kotlin compatibles con JSON (comillas y escapes)


def num(v):
    return "null" if v is None else repr(float(v))


def block(b):
    args = [q(b["block"]), num(b["minutes"]), q(b["zone"])]
    extra = []
    if b["reps"] != 1:
        extra.append(f"reps = {b['reps']}")
    if "rest_minutes" in b:
        extra.append(f"rest = {num(b['rest_minutes'])}")
    if "detail" in b:
        extra.append(f"detail = {q(b['detail'])}")
    if "duty" in b:
        extra.append(f"duty = {num(b['duty'])}")
    return f"B({', '.join(args + extra)})"


def strs(xs):
    return "listOf(" + ", ".join(q(x) for x in xs) + ")"


out = ["// Generado por tools/gen_catalog.py desde el prototipo; desde el port es la fuente de verdad (MOTOR §4 y §5).",
       "package io.github.mrclusterman.trainsync.engine", "",
       "val CATALOG: List<CatalogEntry> = listOf("]
for c in sc.CATALOG:
    blocks = ",\n        ".join(block(b) for b in c["blocks"])
    events = f", events = {strs(c['events'])}" if c.get("events") else ""
    out.append(f"    S({q(c['id'])}, {q(c['sport'])}, {q(c['category'])}, {strs(c['phases'])}, {c['duration'][0]} to {c['duration'][1]},\n"
               f"      listOf(\n        {blocks},\n      ){events}),")
out.append(")")
out.append("")
out.append("val Z3_ALLOWED: Set<String> = setOf(" + ", ".join(q(x) for x in sorted(sc.Z3_ALLOWED)) + ")")
out.append("")
out.append("val NOTES: Map<String, Pair<String, String>> = mapOf(")
out += [f"    {q(k)} to ({q(a)} to {q(b)})," for k, (a, b) in sc.NOTES.items()]
out.append(")")
out.append("")
out.append("/** Fase → (dosis, RPE planificado, minutos). */")
out.append("val STRENGTH_DOSE: Map<String, Triple<String, Int, Int>> = mapOf(")
out += [f"    {q(k)} to Triple({q(d)}, {r}, {m})," for k, (d, r, m) in sc.STRENGTH_DOSE.items()]
out.append(")")
for name in ("LOWER", "PLYO", "UPPER"):
    data = getattr(sc, name)
    out.append("")
    out.append(f"val {name}: Map<String, List<String>> = mapOf(")
    out += [f"    {q(k)} to {strs(v)}," for k, v in data.items()]
    out.append(")")
out.append("")
out.append(f"val CORE: List<String> = {strs(sc.CORE)}")
print("\n".join(out))
