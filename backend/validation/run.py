"""Validación del motor de planes de salud (fase D). Reproducible: python -m validation.run (desde backend/).

Simula 6 perfiles × 3 adherencias durante 24 semanas (6 bloques) y comprueba los criterios V1–V11 de
docs/proyecto/DISENO.md §11. Escribe docs/proyecto/validacion/resultados.md y resultados.csv.
"""
import csv
import statistics
from datetime import date, timedelta
from pathlib import Path

from app.services import health_plan as hp
from app.services.training_engine import WEEKDAYS

START = date(2026, 1, 5)  # lunes fijo: resultados reproducibles
WEEKS = 24
OUT = Path(__file__).resolve().parents[2] / "docs" / "proyecto" / "validacion"
MAIN = ("sentadilla", "bisagra", "empuje horizontal", "tracción horizontal")
VIGOROUS = ("Z4", "Z5", "Z6")

PROFILES = {
    "principiante sedentario": hp.HealthProfile("fitness", {"MON": 45, "WED": 45, "SAT": 60}, strength="bodyweight",
                                                cardio=["walking"], level="beginner", intensity_cap="light_moderate"),
    "persona mayor (70)": hp.HealthProfile("fitness", {"MON": 60, "TUE": 45, "THU": 60, "SAT": 60}, strength="bodyweight",
                                           cardio=["walking", "swimming_pool"], level="beginner", age=70,
                                           intensity_cap="light_moderate", css=150),
    "gimnasio": hp.HealthProfile("strength", {d: 75 for d in ("MON", "TUE", "THU", "FRI", "SAT")}, strength="gym",
                                 cardio=["cycling", "running"], level="intermediate", lthr=170, max_hr=190),
    "corredor": hp.HealthProfile("fitness", {"MON": 60, "TUE": 75, "WED": 60, "THU": 75, "SAT": 120, "SUN": 90},
                                 strength="gym", cardio=["running", "cycling"], level="advanced", lthr=172, max_hr=190),
    "pérdida de grasa": hp.HealthProfile("fat_loss", {d: 60 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")},
                                         strength="gym", cardio=["walking", "cycling"], level="intermediate",
                                         lthr=165, max_hr=185),
    "cribado con tope": hp.HealthProfile("fat_loss", {d: 60 for d in ("MON", "TUE", "WED", "THU", "FRI", "SAT")},
                                         strength="gym", cardio=["running", "cycling"], level="advanced",
                                         intensity_cap="light_moderate", lthr=170, max_hr=190),
}
ADHERENCE = {"alta": 0.95, "media": 0.75, "baja": 0.40}


def simulate(p: hp.HealthProfile, adherence: float) -> list[dict]:
    history, weeks = [], []
    for w in range(WEEKS):
        block, week_in_block = divmod(w, 4)
        weeks.append(hp.week_plan(p, START + timedelta(weeks=w), history[:block], week_in_block))
        if week_in_block == 3:
            history.append(adherence)
    return weeks


def sessions(week: dict, sport: str | None = None) -> list[dict]:
    return [s for d in week["days"] for s in d["sessions"] if sport is None or s["sport"] == sport]


def check(name: str, p: hp.HealthProfile, adherence_label: str, weeks: list[dict]) -> dict[str, tuple[bool, str]]:
    target = hp.TARGET[p.goal]
    stable = [wk for wk in weeks if wk["week_in_block"] < 3 and wk["dose"]["aerobic"] >= target["aerobic"]]
    loading = [wk for wk in weeks if wk["week_in_block"] < 3]
    short = lambda wk: any("llegas a" in w for w in wk["warnings"])  # avisa de que no cabe la dosis
    r = {}

    # V1 suelo OMS (≥ 150 min equivalentes y ≥ 2 días de fuerza) en todas las semanas estables. No depende de la meta
    # del objetivo. No aplica si no hay semanas estables (la progresión aún no ha llegado) o si es físicamente imposible
    # según una estimación independiente del motor: minutos de la semana − 20 por día de fuerza − 15 de calentamiento
    # por día < 150. [Inferencia en la estimación]
    capacity = sum(p.days.values()) - 20 * hp.strength_days(p) - 15 * len(hp.training_days(p))
    low = min((wk["equivalent_min"] for wk in stable), default=0)
    if not stable:
        r["V1"] = (None, "no aplica: sin semanas estables (la progresión aún no ha llegado a la meta)")
    elif capacity < hp.FLOOR_AEROBIC:
        r["V1"] = (None, f"no aplica: con {sum(p.days.values())} min/semana caben ~{capacity} equivalentes; mín. {low}")
    else:
        ok = all(wk["equivalent_min"] >= hp.FLOOR_AEROBIC and wk["dose"]["strength_days"] >= hp.FLOOR_STRENGTH_DAYS
                 for wk in stable)
        r["V1"] = (ok, f"{len(stable)} semanas estables; mín. {low} min equivalentes")

    # V2 mayores: ≥ 3 días de fuerza con equilibrio
    if p.age and p.age >= 65:
        ok = all(sum(hp.BALANCE in s["exercises"] for s in sessions(wk, "strength")) >= 3 for wk in weeks)
        r["V2"] = (ok, "equilibrio y fuerza ≥ 3 días todas las semanas")

    # V3 fuerza y músculo: cada patrón principal ≥ 2 veces y series semanales en la meta (o aviso)
    if p.goal == "strength":
        times = all(sum(pt in s["patterns"] for s in sessions(wk, "strength")) >= 2 for wk in weeks for pt in MAIN)
        sets_ok = all(min(sum(s["sets_per_exercise"] for s in sessions(wk, "strength") if pt in s["patterns"])
                          for pt in MAIN) >= wk["dose"]["sets"] or any("series" in w for w in wk["warnings"])
                      for wk in weeks)
        r["V3"] = (times and sets_ok, "cada patrón principal ≥ 2 veces/semana; series = dosis o aviso")

    # V4 pérdida de grasa: ≥ 250 min si caben; si no, aviso
    if p.goal == "fat_loss":
        top = [wk for wk in stable]
        ok = all(wk["equivalent_min"] >= 250 or short(wk) for wk in top)
        r["V4"] = (ok, f"semanas estables: {len(top)}; con aviso: {sum(short(wk) for wk in top)}")

    # V5 progresión ≤ 10 % semanal dentro del bloque y entre bloques; semana 4 de descarga
    aero = [wk["dose"]["aerobic"] for wk in weeks]
    rises = [aero[i] / aero[i - 1] for i in range(1, WEEKS) if weeks[i]["week_in_block"] in (1, 2)]
    rebounds = [aero[i] / aero[i - 2] for i in range(4, WEEKS, 4)]  # inicio de bloque frente al pico anterior
    deload = all(weeks[i]["dose"]["aerobic"] < weeks[i - 1]["dose"]["aerobic"] or weeks[i - 1]["dose"]["aerobic"] == 0
                 for i in range(3, WEEKS, 4))
    ok = max(rises + rebounds, default=1) <= 1.105 and deload
    r["V5"] = (ok, f"subida máx. {max(rises + rebounds, default=1) - 1:+.1%}; descarga cada 4.ª semana: {deload}")

    # V6 tope del cribado
    if p.intensity_cap != "vigorous":
        cardio = [s for wk in weeks for s in sessions(wk) if s["sport"] != "strength"]
        no_vig = all(b["zone"] not in VIGOROUS for s in cardio for b in s["blocks"])
        rir = all(s["rir"] >= 3 for wk in weeks for s in sessions(wk, "strength"))
        r["V6"] = (no_vig and rir, "sin Z4+ ni RIR < 3")

    # V7 descanso semanal y pierna pesada nunca en días seguidos
    rest = all(any(not d["sessions"] for d in wk["days"]) for wk in weeks)
    legs = all(not (any("sentadilla" in s.get("patterns", []) for s in a["sessions"]) and
                    any("sentadilla" in s.get("patterns", []) for s in b["sessions"]))
               for wk in weeks for a, b in zip(wk["days"], wk["days"][1:]))
    r["V7"] = (rest and legs, f"día de descanso: {rest}; pierna en días seguidos: {not legs}")

    # V8 fuerza y músculo: nada de carrera el día de pierna si hay alternativa
    if p.goal == "strength" and len(p.cardio) > 1:
        ok = all(not (any("sentadilla" in s.get("patterns", []) for s in d["sessions"]) and
                      any(s["sport"] == "running" for s in d["sessions"])) for wk in weeks for d in wk["days"])
        r["V8"] = (ok, "sin carrera el día de pierna")

    # V9 monotonía semanal de Foster (media / desviación de la carga diaria) < 2, en todas las semanas de carga; se
    # desglosa por semanas limitadas o no por el tiempo disponible (cuando la dosis no cabe, el motor prioriza minutos).
    def monotony(wk):
        loads = [sum(s["tss"] for s in d["sessions"]) for d in wk["days"]]
        sd = statistics.pstdev(loads)
        return statistics.mean(loads) / sd if sd else 0
    free_weeks = [monotony(wk) for wk in loading if not short(wk)]
    bound_weeks = [monotony(wk) for wk in loading if short(wk)]
    detail = f"sin límite de tiempo: máx. {max(free_weeks, default=0):.2f} ({len(free_weeks)} semanas)"
    if bound_weeks:
        detail += f"; limitadas por tiempo: máx. {max(bound_weeks):.2f} ({len(bound_weeks)} semanas, no evaluadas)"
    r["V9"] = (max(free_weeks + bound_weeks) < 2, detail.replace(", no evaluadas", ""))

    # V10 determinismo
    again = simulate(p, ADHERENCE[adherence_label])
    r["V10"] = (again == weeks, "dos ejecuciones idénticas")
    return r


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    rows, lines = [], []
    finals = {}
    for name, p in PROFILES.items():
        for label, value in ADHERENCE.items():
            weeks = simulate(p, value)
            finals[(name, label)] = weeks[-4]["dose"]["aerobic"]  # primera semana del último bloque
            for crit, (ok, detail) in check(name, p, label, weeks).items():
                rows.append({"perfil": name, "adherencia": label, "criterio": crit, "cumple": ok, "detalle": detail})
    # V11 adherencia baja → menos volumen que alta
    for name in PROFILES:
        ok = finals[(name, "baja")] < finals[(name, "alta")] or finals[(name, "alta")] == hp.TARGET[PROFILES[name].goal]["aerobic"] == finals[(name, "baja")] == 0
        rows.append({"perfil": name, "adherencia": "baja vs alta", "criterio": "V11", "cumple": ok,
                     "detalle": f"último bloque: {finals[(name, 'baja')]} min (baja) frente a {finals[(name, 'alta')]} (alta)"})

    with open(OUT / "resultados.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    applicable = [r for r in rows if r["cumple"] is not None]
    passed = sum(bool(r["cumple"]) for r in applicable)
    lines = [f"# Resultados de la validación ({START.isoformat()}, {WEEKS} semanas)", "",
             f"{passed} de {len(applicable)} comprobaciones aplicables cumplen ({len(rows) - len(applicable)} no aplican). "
             "Generado con `python -m validation.run`.", "",
             "| Perfil | Adherencia | Criterio | Cumple | Detalle |", "|---|---|---|---|---|"]
    mark = {True: "sí", False: "**no**", None: "no aplica"}
    lines += [f"| {r['perfil']} | {r['adherencia']} | {r['criterio']} | {mark[r['cumple']]} | {r['detalle']} |"
              for r in rows]
    (OUT / "resultados.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines[:3]))
    for r in rows:
        if r["cumple"] is False:
            print("NO CUMPLE:", r)


if __name__ == "__main__":
    main()
