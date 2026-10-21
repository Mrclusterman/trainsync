"use client";

import { useActionState } from "react";
import { createGoalAction, deleteGoalAction } from "@/lib/account-actions";
import type { RaceGoal } from "@/lib/account";

const SPORTS = [["running", "Carrera a pie"], ["cycling", "Ciclismo"], ["triathlon", "Triatlón"], ["duathlon", "Duatlón"],
  ["swimming_pool", "Natación (piscina)"], ["swimming_open", "Aguas abiertas"]] as const;
const LABEL = Object.fromEntries(SPORTS) as Record<string, string>;
const field = "w-full rounded-card bg-surface-2 px-3 py-2 text-sm";

export function GoalsForm({ goals }: { goals: RaceGoal[] }) {
  const [state, create, saving] = useActionState(createGoalAction, null);
  const today = new Date().toLocaleDateString("sv-SE");
  return (
    <div className="space-y-3">
      {goals.length > 0 && (
        <ul className="space-y-2">
          {goals.map((g) => (
            <li key={g.id} className="flex items-center justify-between gap-2 rounded-card bg-surface-2 p-3 text-sm">
              <span>
                <span className="font-medium">{g.name}</span>
                <span className="block text-xs text-secondary">
                  {g.date} · {LABEL[g.sport]}{g.distance_km ? ` · ${g.distance_km} km` : ""} · prioridad {g.priority}{g.trail ? " · trail" : ""}
                </span>
              </span>
              <form action={deleteGoalAction}>
                <input type="hidden" name="id" value={g.id} />
                <button className="font-ui text-xs text-danger" aria-label={`Borrar ${g.name}`}>Borrar</button>
              </form>
            </li>
          ))}
        </ul>
      )}
      <form action={create} className="space-y-2">
        <input name="name" required maxLength={80} placeholder="Nombre (p. ej. Medio maratón de Alicante)" className={field} />
        <div className="grid grid-cols-2 gap-2">
          <select name="sport" defaultValue="running" aria-label="Deporte" className={field}>
            {SPORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <input name="date" type="date" required min={today} aria-label="Fecha" className={field} />
          <input name="distance_km" type="number" step="0.1" min="0.1" max="1000" placeholder="Distancia (km)" className={field} />
          <select name="priority" defaultValue="A" aria-label="Prioridad" className={field}>
            <option value="A">Prioridad A (la importante)</option>
            <option value="B">Prioridad B</option>
            <option value="C">Prioridad C (de preparación)</option>
          </select>
        </div>
        <label className="flex gap-2 text-sm"><input type="checkbox" name="trail" />Es de montaña (trail)</label>
        {state?.error && <p role="alert" className="text-sm text-alert">{state.error}</p>}
        {state?.message && <p role="status" className="text-sm text-bio">{state.message}</p>}
        <button disabled={saving} className="rounded-full bg-surface-2 px-4 py-2 font-ui text-sm">{saving ? "Guardando…" : "Añadir objetivo"}</button>
      </form>
    </div>
  );
}
