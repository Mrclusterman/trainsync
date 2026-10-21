import { cookies } from "next/headers";
import type { components } from "./api-types";

export type UserPlan = components["schemas"]["UserPlan"];
export type HealthDay = components["schemas"]["HealthDay"];
export type HealthStrength = components["schemas"]["HealthStrength"];
export type HealthCardio = components["schemas"]["HealthCardio"];

const API_URL = process.env.TRAINSYNC_API_URL ?? "http://localhost:8000";
export const TOKEN_COOKIE = "ts_session";

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

// Solo en el servidor: el token va en una cookie httpOnly y el navegador nunca habla con el backend
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = (await cookies()).get(TOKEN_COOKIE)?.value;
  const res = await fetch(`${API_URL}${path}`, {
    ...init, cache: "no-store",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const detail = Array.isArray(body?.detail) ? "Revisa los datos del formulario" : body?.detail;
    throw new ApiError(res.status, detail ?? `El backend respondió ${res.status}`);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export async function hasSession() {
  return Boolean((await cookies()).get(TOKEN_COOKIE)?.value);
}
