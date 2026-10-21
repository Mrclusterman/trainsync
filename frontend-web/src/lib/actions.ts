"use server";

import { sendToday } from "./api";

export type SendResult = { ok: boolean; message: string };

// Server Action: el navegador no habla con el backend (sin CORS y sin exponer su URL)
// sport: la alternativa que vas a hacer (null = la principal); va ligado con bind desde el botón
export async function sendTodayAction(sport: string | null): Promise<SendResult> {
  try {
    await sendToday(sport);
    return { ok: true, message: "Enviada al calendario de intervals.icu. Llegará a tu reloj si tienes activada la sincronización." };
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : "No se pudo enviar la sesión" };
  }
}
