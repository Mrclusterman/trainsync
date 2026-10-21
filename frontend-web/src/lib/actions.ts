"use server";

import { sendToday } from "./api";

export type SendResult = { ok: boolean; message: string };

// Server Action: el navegador no habla con el backend (sin CORS y sin exponer su URL)
export async function sendTodayAction(): Promise<SendResult> {
  try {
    await sendToday();
    return { ok: true, message: "Enviada al calendario de intervals.icu. Llegará a tu reloj si tienes activada la sincronización." };
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : "No se pudo enviar la sesión" };
  }
}
