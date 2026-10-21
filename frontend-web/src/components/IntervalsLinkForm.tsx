"use client";

import { useActionState } from "react";
import { linkIntervalsAction } from "@/lib/account-actions";

export function IntervalsLinkForm({ linked }: { linked: boolean }) {
  const [state, link, linking] = useActionState(linkIntervalsAction, null);
  return (
    <form action={link} className="space-y-2">
      <p className="text-sm text-secondary">
        {linked ? "Vinculada. Puedes cambiarla aquí." : "Opcional: con tu reloj, el plan usa tu FC y envía las sesiones a tu calendario."}
        {" "}La clave está en intervals.icu → Settings → Developer Settings y se guarda cifrada.
      </p>
      <input name="athlete_id" required placeholder="ID de atleta (i12345)" className="w-full rounded-card bg-surface-2 px-3 py-2" />
      <input name="api_key" type="password" required placeholder="Clave de la API" className="w-full rounded-card bg-surface-2 px-3 py-2" />
      {state?.error && <p role="alert" className="text-sm text-alert">{state.error}</p>}
      {state?.message && <p role="status" className="text-sm text-bio">{state.message}</p>}
      <button disabled={linking} className="rounded-full bg-surface-2 px-4 py-2 font-ui text-sm">{linking ? "Comprobando…" : "Vincular"}</button>
    </form>
  );
}
