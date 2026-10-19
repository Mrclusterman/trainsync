import type { Day, State, Zone } from "./api";

export const SPORT_LABEL: Record<string, string> = {
  running: "Carrera",
  cycling: "Ciclismo",
  swimming_pool: "Natación",
  swimming_open: "Aguas abiertas",
  triathlon: "Triatlón",
  duathlon: "Duatlón",
  strength: "Fuerza",
};

export const PHASE_LABEL: Record<string, string> = {
  base_early: "Base temprana",
  base: "Base",
  build: "Construcción",
  peak: "Peak",
  taper: "Taper",
};

export const WEEKDAY_LABEL: Record<string, string> = {
  MON: "Lun", TUE: "Mar", WED: "Mié", THU: "Jue", FRI: "Vie", SAT: "Sáb", SUN: "Dom",
};

export const ZONE_COLOR: Record<Zone, string> = {
  Z1: "var(--zone-1)", Z2: "var(--zone-2)", Z3: "var(--zone-3)",
  Z4: "var(--zone-4)", Z5: "var(--zone-5)", Z6: "var(--zone-6)",
};

// TSB según Memoria v0.4 (Friel): -10/-30 es carga productiva, no fatiga. El naranja queda para zona gris y avisos.
export function tsbTone(tsb: number): { color: string; label: string } {
  if (tsb > 5) return { color: "var(--color-bio)", label: "Fresco" };
  if (tsb >= -10) return { color: "var(--color-text-primary)", label: "Neutro" };
  if (tsb >= -30) return { color: "var(--color-accent)", label: "Cargando" };
  return { color: "var(--color-danger)", label: "Riesgo alto" };
}

// Estado del atleta (diseño: óptimo / normal / fatigado) a partir del estado y de los ajustes de hoy
export function athleteMood(state: State, today: Day): "optimal" | "normal" | "fatigued" {
  if (state.tsb < -30 || today.reasons.some((r) => !r.startsWith("Semana de descarga") && !r.startsWith("Test"))) {
    return "fatigued";
  }
  if (state.tsb > 10 && state.hrv_status === "within_swc" && (state.sleep_score ?? 0) > 75) return "optimal";
  return "normal";
}

export function minutes(total: number): string {
  const h = Math.floor(total / 60);
  const m = Math.round(total % 60);
  return h ? `${h} h ${m.toString().padStart(2, "0")}′` : `${m}′`;
}

export function shortDate(iso: string): string {
  return new Date(`${iso}T12:00:00`).toLocaleDateString("es-ES", { day: "numeric", month: "short" });
}
