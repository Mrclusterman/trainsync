"use client";

import { useActionState, useState } from "react";
import { createGoalAction, deleteGoalAction, updateGoalAction, type FormState } from "@/lib/account-actions";
import type { RaceGoal } from "@/lib/account";

const SPORTS = [["running", "Carrera a pie"], ["cycling", "Ciclismo"], ["triathlon", "Triatlón"], ["duathlon", "Duatlón"],
  ["swimming_pool", "Natación (piscina)"], ["swimming_open", "Aguas abiertas"]] as const;
const LABEL = Object.fromEntries(SPORTS) as Record<string, string>;
const field = "w-full rounded-card bg-surface-2 px-3 py-2 text-sm";
const small = "rounded-full bg-surface-2 px-4 py-2 font-ui text-sm";

function Status({ state }: { state: FormState }) {
  return (
    <>
      {state?.error && <p role="alert" className="text-sm text-alert">{state.error}</p>}
      {state?.message && <p role="status" className="text-sm text-bio">{state.message}</p>}
    </>
  );
}

// Campos comunes a crear y editar
function GoalFields({ goal }: { goal?: RaceGoal }) {
  const today = new Date().toLocaleDateString("sv-SE");
  return (
    <>
      <input name="name" required maxLength={80} defaultValue={goal?.name} placeholder="Nombre (p. ej. Medio maratón de Alicante)"
        aria-label="Nombre" className={field} />
      <div className="grid grid-cols-2 gap-2">
        <select name="sport" defaultValue={goal?.sport ?? "running"} aria-label="Deporte" className={field}>
          {SPORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <input name="date" type="date" required min={today} defaultValue={goal?.date} aria-label="Fecha" className={field} />
        <input name="distance_km" type="number" step="0.1" min="0.1" max="1000" defaultValue={goal?.distance_km ?? ""}
          placeholder="Distancia (km)" aria-label="Distancia en km" className={field} />
        <select name="priority" defaultValue={goal?.priority ?? "A"} aria-label="Prioridad" className={field}>
          <option value="A">Prioridad A (la importante)</option>
          <option value="B">Prioridad B</option>
          <option value="C">Prioridad C (de preparación)</option>
        </select>
      </div>
      <label className="flex gap-2 text-sm"><input type="checkbox" name="trail" defaultChecked={goal?.trail} />Es de montaña (trail)</label>
    </>
  );
}

function GoalItem({ goal }: { goal: RaceGoal }) {
  const [editing, setEditing] = useState(false);
  const [state, update, saving] = useActionState(updateGoalAction, null);
  if (editing && !state?.message) {
    return (
      <li className="rounded-card bg-surface-2 p-3">
        <form action={update} className="space-y-2">
          <input type="hidden" name="id" value={goal.id} />
          <GoalFields goal={goal} />
          <Status state={state} />
          <div className="flex gap-3">
            <button disabled={saving} className="rounded-full bg-accent px-4 py-2 font-ui text-sm text-canvas">{saving ? "Guardando…" : "Guardar cambios"}</button>
            <button type="button" onClick={() => setEditing(false)} className="font-ui text-sm text-secondary">Cancelar</button>
          </div>
        </form>
      </li>
    );
  }
  return (
    <li className="flex items-center justify-between gap-2 rounded-card bg-surface-2 p-3 text-sm">
      <span>
        <span className="font-medium">{goal.name}</span>
        <span className="block text-xs text-secondary">
          {goal.date} · {LABEL[goal.sport]}{goal.distance_km ? ` · ${goal.distance_km} km` : ""} · prioridad {goal.priority}{goal.trail ? " · trail" : ""}
        </span>
        <Status state={state} />
      </span>
      <span className="flex gap-3">
        <button type="button" onClick={() => setEditing(true)} className="font-ui text-xs text-accent" aria-label={`Editar ${goal.name}`}>Editar</button>
        <form action={deleteGoalAction}>
          <input type="hidden" name="id" value={goal.id} />
          <button className="font-ui text-xs text-danger" aria-label={`Borrar ${goal.name}`}>Borrar</button>
        </form>
      </span>
    </li>
  );
}

export function GoalsForm({ goals }: { goals: RaceGoal[] }) {
  const [state, create, saving] = useActionState(createGoalAction, null);
  return (
    <div className="space-y-3">
      {goals.length > 0 && <ul className="space-y-2">{goals.map((g) => <GoalItem key={`${g.id}-${g.date}-${g.name}`} goal={g} />)}</ul>}
      <form action={create} className="space-y-2">
        <GoalFields />
        <Status state={state} />
        <button disabled={saving} className={small}>{saving ? "Guardando…" : "Añadir objetivo"}</button>
      </form>
    </div>
  );
}
