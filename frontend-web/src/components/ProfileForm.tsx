"use client";

import { useActionState } from "react";
import { saveProfileAction } from "@/lib/account-actions";
import { HealthNotice } from "./HealthNotice";
import { WEEKDAY_LABEL } from "@/lib/format";

export type SavedProfile = {
  goal: string; days: Record<string, number>; strength: string; cardio: string[]; level: string;
} | null;

const GOALS = [
  ["fitness", "Ponerme en forma", "Resistencia y fuerza equilibradas"],
  ["strength", "Fuerza y músculo", "Más gimnasio; la resistencia, de complemento"],
  ["fat_loss", "Perder grasa", "Más minutos de actividad aeróbica y fuerza para conservar músculo"],
] as const;
const CARDIO = [["walking", "Caminar"], ["running", "Correr"], ["cycling", "Bici"], ["swimming_pool", "Nadar"]] as const;
const legend = "font-ui text-sm text-secondary";
const box = "rounded-module bg-surface-1 p-4 space-y-2";

export function ProfileForm({ saved }: { saved: SavedProfile }) {
  const [state, save, saving] = useActionState(saveProfileAction, null);
  const p = saved ?? { goal: "fitness", days: { MON: 45, WED: 45, FRI: 45 }, strength: "gym", cardio: ["walking"], level: "beginner" };
  return (
    <form action={save} className="space-y-4">
      <fieldset className={box}>
        <legend className={legend}>Objetivo</legend>
        {GOALS.map(([value, label, hint]) => (
          <label key={value} className="flex gap-2">
            <input type="radio" name="goal" value={value} defaultChecked={p.goal === value} required className="mt-1" />
            <span>{label}<span className="block text-xs text-secondary">{hint}</span></span>
          </label>
        ))}
      </fieldset>

      <fieldset className={box}>
        <legend className={legend}>Días y minutos disponibles</legend>
        {Object.entries(WEEKDAY_LABEL).map(([day, label]) => (
          <div key={day} className="flex items-center gap-3">
            <label className="flex w-20 gap-2"><input type="checkbox" name={`day_${day}`} defaultChecked={day in p.days} />{label}</label>
            <input type="number" name={`min_${day}`} min={15} max={240} step={5} defaultValue={p.days[day] ?? 45}
              aria-label={`Minutos el ${label}`} className="w-20 rounded-card bg-surface-2 px-2 py-1" />
            <span className="text-xs text-secondary">min</span>
          </div>
        ))}
      </fieldset>

      <fieldset className={box}>
        <legend className={legend}>Fuerza</legend>
        {[["gym", "En el gimnasio"], ["bodyweight", "Con mi peso, en casa"], ["none", "Sin fuerza"]].map(([v, l]) => (
          <label key={v} className="flex gap-2"><input type="radio" name="strength" value={v} defaultChecked={p.strength === v} />{l}</label>
        ))}
      </fieldset>

      <fieldset className={box}>
        <legend className={legend}>Actividad aeróbica que te gusta</legend>
        {CARDIO.map(([v, l]) => (
          <label key={v} className="flex gap-2"><input type="checkbox" name="cardio" value={v} defaultChecked={p.cardio.includes(v)} />{l}</label>
        ))}
      </fieldset>

      <fieldset className={box}>
        <legend className={legend}>Nivel</legend>
        {[["beginner", "Empiezo o vuelvo tras un parón"], ["intermediate", "Entreno con regularidad"],
          ["advanced", "Llevo años entrenando"]].map(([v, l]) => (
          <label key={v} className="flex gap-2"><input type="radio" name="level" value={v} defaultChecked={p.level === v} />{l}</label>
        ))}
      </fieldset>

      <HealthNotice />

      {state?.error && <p role="alert" className="text-sm text-alert">{state.error}</p>}
      <button disabled={saving} className="w-full rounded-full bg-accent px-5 py-2.5 font-ui text-sm font-medium text-canvas disabled:opacity-60">
        {saving ? "Guardando…" : "Guardar y ver mi plan"}
      </button>
    </form>
  );
}
