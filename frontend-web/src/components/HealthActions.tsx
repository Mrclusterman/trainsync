"use client";

import { useActionState } from "react";
import { readinessAction, sendHealthTodayAction } from "@/lib/account-actions";

const QUESTIONS = [["sleep", "¿Cómo has dormido?"], ["energy", "¿Cómo tienes la energía?"], ["soreness", "¿Agujetas o molestias?"]] as const;

// Sin reloj (DISENO §8): tres respuestas de 1 (mal) a 5 (bien) ajustan la sesión de hoy
export function ReadinessForm() {
  const [state, send, sending] = useActionState(readinessAction, null);
  return (
    <form action={send} className="rounded-module bg-surface-1 p-4">
      <p className="font-ui text-sm text-secondary">¿Cómo estás hoy?</p>
      <div className="mt-2 space-y-2">
        {QUESTIONS.map(([name, label]) => (
          <label key={name} className="flex items-center justify-between gap-3 text-sm">{label}
            <select name={name} defaultValue="3" className="rounded-card bg-surface-2 px-2 py-1">
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}{n === 1 ? " · mal" : n === 5 ? " · bien" : ""}</option>)}
            </select>
          </label>
        ))}
      </div>
      <button disabled={sending} className="mt-3 rounded-full bg-surface-2 px-4 py-2 font-ui text-sm">{sending ? "Ajustando…" : "Ajustar hoy"}</button>
      {state?.message && <p role="status" className="mt-2 text-sm text-bio">{state.message}</p>}
      {state?.error && <p role="alert" className="mt-2 text-sm text-alert">{state.error}</p>}
    </form>
  );
}

export function SendHealthToday() {
  const [state, send, sending] = useActionState(sendHealthTodayAction, null);
  return (
    <form action={send} className="flex flex-wrap items-center gap-3">
      <button disabled={sending} className="rounded-full bg-accent px-5 py-2 font-ui text-sm font-medium text-canvas disabled:opacity-60">
        {sending ? "Enviando…" : "Enviar hoy a mi reloj"}
      </button>
      {state?.message && <p role="status" className="text-sm text-bio">{state.message}</p>}
      {state?.error && <p role="alert" className="text-sm text-alert">{state.error}</p>}
    </form>
  );
}
