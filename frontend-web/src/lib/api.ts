import type { components } from "./api-types";

export type State = components["schemas"]["State"];
export type Plan = components["schemas"]["Plan"];
export type Day = components["schemas"]["Day"];
export type Session = components["schemas"]["Session"];
export type Strength = components["schemas"]["Strength"];
export type Block = components["schemas"]["Block"];
export type Zone = Block["zone"];

const API_URL = process.env.TRAINSYNC_API_URL ?? "http://localhost:8000";

// Solo se llama desde Server Components: sin CORS y sin exponer la URL del backend al navegador
async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, { cache: "no-store" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.detail ?? `El backend respondió ${res.status} en ${path}`);
  }
  return res.json();
}

export const getState = () => get<State>("/state");
export const getPlan = (days = 14) => get<Plan>(`/plan?days=${days}`);
