"use client";

import { useActionState } from "react";
import { sendTodayAction, type SendResult } from "@/lib/actions";

export function SendToWatch({ sport = null, label = "Enviar a mi reloj" }: { sport?: string | null; label?: string }) {
  const [result, send, pending] = useActionState<SendResult | null>(sendTodayAction.bind(null, sport), null);
  return (
    <form action={send} className="flex flex-wrap items-center gap-3">
      <button type="submit" disabled={pending}
        className="rounded-full bg-accent px-5 py-2 font-ui text-sm font-medium text-canvas disabled:opacity-60">
        {pending ? "Enviando…" : result?.ok ? "Volver a enviar" : label}
      </button>
      {result && (
        <p role="status" className={`text-sm ${result.ok ? "text-bio" : "text-alert"}`}>{result.message}</p>
      )}
    </form>
  );
}
