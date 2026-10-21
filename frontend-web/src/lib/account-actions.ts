"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, ApiError, TOKEN_COOKIE } from "./account";

export type FormState = { error?: string; message?: string } | null;
const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

async function startSession(token: string) {
  (await cookies()).set(TOKEN_COOKIE, token, {
    httpOnly: true, sameSite: "lax", secure: process.env.NODE_ENV === "production", path: "/", maxAge: 7 * 24 * 3600,
  });
}

const fail = (e: unknown): FormState => ({ error: e instanceof Error ? e.message : "No se pudo completar" });

export async function loginAction(_: FormState, form: FormData): Promise<FormState> {
  try {
    const { token } = await api<{ token: string }>("/auth/login", {
      method: "POST", body: JSON.stringify({ email: form.get("email"), password: form.get("password") }),
    });
    await startSession(token);
  } catch (e) { return fail(e); }
  redirect("/salud");
}

export async function registerAction(_: FormState, form: FormData): Promise<FormState> {
  try {
    const { token } = await api<{ token: string }>("/auth/register", {
      method: "POST", body: JSON.stringify({
        email: form.get("email"), password: form.get("password"), birth_year: Number(form.get("birth_year")),
        accept_terms: form.get("accept_terms") === "on", health_consent: form.get("health_consent") === "on",
      }),
    });
    await startSession(token);
  } catch (e) { return fail(e); }
  redirect("/salud/perfil");
}

export async function logoutAction() {
  (await cookies()).delete(TOKEN_COOKIE);
  redirect("/entrar");
}

export async function saveProfileAction(_: FormState, form: FormData): Promise<FormState> {
  const days = Object.fromEntries(WEEKDAYS.filter((d) => form.get(`day_${d}`) === "on")
    .map((d) => [d, Number(form.get(`min_${d}`) || 45)]));
  if (Object.keys(days).length === 0) return { error: "Elige al menos un día" };
  try {
    await api("/me/profile", {
      method: "PUT", body: JSON.stringify({
        goal: form.get("goal"), days, strength: form.get("strength"), level: form.get("level"),
        cardio: form.getAll("cardio"),
      }),
    });
  } catch (e) { return fail(e); }
  redirect("/salud");
}

export async function logSessionAction(form: FormData) {
  await api("/me/sessions", { method: "POST", body: JSON.stringify({ date: form.get("date"), sport: form.get("sport") }) });
  revalidatePath("/salud");
}

export async function readinessAction(_: FormState, form: FormData): Promise<FormState> {
  try {
    await api("/me/readiness", {
      method: "POST", body: JSON.stringify({
        sleep: Number(form.get("sleep")), energy: Number(form.get("energy")), soreness: Number(form.get("soreness")),
      }),
    });
  } catch (e) { return fail(e); }
  revalidatePath("/salud");
  return { message: "Plan de hoy actualizado" };
}

export async function sendHealthTodayAction(): Promise<FormState> {
  try {
    const sent = await api<{ name: string }[]>("/me/plan/today/send", { method: "POST" });
    return { message: `Enviado a intervals.icu: ${sent.map((s) => s.name.replace("TrainSync · ", "")).join(", ")}` };
  } catch (e) { return fail(e); }
}

export async function linkIntervalsAction(_: FormState, form: FormData): Promise<FormState> {
  try {
    await api("/me/intervals", {
      method: "PUT", body: JSON.stringify({ athlete_id: form.get("athlete_id"), api_key: form.get("api_key") }),
    });
  } catch (e) { return fail(e); }
  return { message: "Cuenta de intervals.icu vinculada" };
}

export async function deleteAccountAction(form: FormData) {
  if (form.get("confirm") !== "BORRAR") return;
  try { await api("/me", { method: "DELETE" }); } catch (e) { if (!(e instanceof ApiError && e.status === 401)) throw e; }
  (await cookies()).delete(TOKEN_COOKIE);
  redirect("/entrar");
}

// Mejora 3 (replica/fixes.md): cambiar la semana desde el móvil; el backend la recalcula desde hoy
export async function moveSessionAction(form: FormData) {
  await api("/me/week/move", { method: "POST", body: JSON.stringify({ from_date: form.get("from"), to_date: form.get("to") }) });
  revalidatePath("/salud");
}

export async function changeDayAction(form: FormData) {
  await api("/me/week/day", { method: "POST", body: JSON.stringify({ date: form.get("date"), minutes: Number(form.get("minutes")) }) });
  revalidatePath("/salud");
}

export async function resetWeekAction() {
  await api("/me/week", { method: "DELETE" });
  revalidatePath("/salud");
}

// Carreras objetivo creadas en la app (el plan de carrera también lee las de intervals.icu)
export async function createGoalAction(_: FormState, form: FormData): Promise<FormState> {
  const distance = form.get("distance_km");
  let synced: boolean | null | undefined;
  try {
    ({ synced } = await api<{ synced?: boolean | null }>("/me/goals", {
      method: "POST", body: JSON.stringify({
        name: form.get("name"), sport: form.get("sport"), date: form.get("date"), priority: form.get("priority"),
        distance_km: distance ? Number(distance) : null, trail: form.get("trail") === "on",
      }),
    }));
  } catch (e) { return fail(e); }
  revalidatePath("/salud/perfil");
  revalidatePath("/");
  if (synced === false) return { error: "Guardado en la app, pero no se pudo copiar a intervals.icu. Prueba más tarde." };
  return { message: synced ? "Guardado y copiado a tu calendario de intervals.icu" : "Objetivo guardado" };
}

export async function deleteGoalAction(form: FormData) {
  await api(`/me/goals/${Number(form.get("id"))}`, { method: "DELETE" });
  revalidatePath("/salud/perfil");
  revalidatePath("/");
}
