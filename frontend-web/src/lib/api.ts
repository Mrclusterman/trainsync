import { api } from "./account";
import type { components } from "./api-types";

export type State = components["schemas"]["State"];
export type Plan = components["schemas"]["Plan"];
export type Day = components["schemas"]["Day"];
export type Session = components["schemas"]["Session"];
export type Strength = components["schemas"]["Strength"];
export type Block = components["schemas"]["Block"];
export type Zone = Block["zone"];

// Plan de carrera: con la sesión del usuario y su propia cuenta de intervals.icu (nunca una del servidor)
export const getState = () => api<State>("/state");
export const getPlan = (days = 14) => api<Plan>(`/plan?days=${days}`);

export type Sent = components["schemas"]["Sent"];

export const sendToday = () => api<Sent>("/plan/today/send", { method: "POST" });
