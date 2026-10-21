import Link from "next/link";
import { Suspense } from "react";
import { redirect } from "next/navigation";
import { ReadinessForm, SendHealthToday } from "@/components/HealthActions";
import { DashboardSkeleton } from "@/components/Skeletons";
import { SportIcon } from "@/components/SportIcon";
import { api, ApiError, hasSession, type HealthCardio, type HealthDay, type HealthStrength, type UserPlan } from "@/lib/account";
import { logSessionAction } from "@/lib/account-actions";
import { minutes, SPORT_LABEL, WEEKDAY_LABEL } from "@/lib/format";

const GOAL_LABEL: Record<string, string> = { fitness: "Ponerme en forma", strength: "Fuerza y músculo", fat_loss: "Perder grasa" };

function Session({ s, date, past, done }: { s: HealthStrength | HealthCardio; date: string; past: boolean; done: boolean }) {
  return (
    <article className="rounded-card bg-surface-2 p-3">
      <div className="flex items-center gap-2">
        <SportIcon sport={s.sport} size={18} />
        <h4 className="font-ui text-sm font-medium">{s.sport === "strength" ? s.name : `${SPORT_LABEL[s.sport] ?? s.sport} · ${s.name}`}</h4>
        <span className="ml-auto font-mono text-xs text-secondary">{minutes(s.duration_min)}</span>
      </div>
      {"exercises" in s ? (
        <>
          <ul className="mt-2 space-y-1 text-sm">{s.exercises.map((e) => <li key={e}>{e}</li>)}</ul>
          <p className="mt-2 text-xs text-secondary">{s.notes}</p>
        </>
      ) : (
        <p className="mt-1 text-sm text-secondary">{s.goal} · {s.equivalent_min} min moderados equivalentes</p>
      )}
      {done && <p className="mt-2 font-ui text-xs text-bio">Hecha</p>}
      {past && !done && (
        <form action={logSessionAction} className="mt-2">
          <input type="hidden" name="date" value={date} />
          <input type="hidden" name="sport" value={s.sport} />
          <button className="rounded-full border border-accent/40 px-3 py-1 font-ui text-xs text-accent">Marcar como hecha</button>
        </form>
      )}
    </article>
  );
}

function Day({ day, today }: { day: HealthDay; today: string }) {
  const isToday = day.date === today;
  return (
    <section aria-label={`${WEEKDAY_LABEL[day.weekday]} ${day.date}`}
      className={`rounded-module bg-surface-1 p-3 ${isToday ? "ring-1 ring-accent/50" : ""}`}>
      <h3 className="font-ui text-sm text-secondary">{WEEKDAY_LABEL[day.weekday]} {day.date.slice(8)}{isToday && " · hoy"}</h3>
      {day.note && <p className="mt-1 text-sm text-alert">{day.note}</p>}
      {day.sessions.length === 0 ? <p className="mt-1 text-sm text-secondary">Descanso</p> : (
        <div className="mt-2 space-y-2">
          {day.sessions.map((s) => <Session key={s.sport} s={s} date={day.date} past={day.date <= today} done={day.done?.includes(s.sport) ?? false} />)}
        </div>
      )}
    </section>
  );
}

async function SaludView() {
  if (!(await hasSession())) redirect("/entrar");
  let plan: UserPlan;
  try {
    plan = await api<UserPlan>("/me/plan");
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) redirect("/salud/perfil");
    if (e instanceof ApiError && e.status === 401) redirect("/entrar");
    throw e;
  }
  const today = new Date().toLocaleDateString("sv-SE");  // AAAA-MM-DD en hora local
  const deload = plan.week_in_block === 3;
  return (
    <div className="space-y-4 pt-4">
      <header className="flex items-baseline justify-between">
        <h1 className="font-ui text-2xl font-bold">{GOAL_LABEL[plan.goal]}</h1>
        <Link href="/salud/perfil" className="font-ui text-sm text-accent">Perfil</Link>
      </header>
      <p className="text-sm text-secondary">
        Bloque {plan.block + 1}, semana {plan.week_in_block + 1} de 4{deload && " (descarga)"} · {plan.equivalent_min} de{" "}
        {plan.dose.aerobic} min aeróbicos · fuerza {plan.dose.strength_days} días
      </p>
      {plan.warnings.map((w) => <p key={w} className="rounded-card border-l-2 border-alert bg-surface-1 p-3 text-sm">{w}</p>)}
      <ReadinessForm />
      <SendHealthToday />
      <div className="space-y-3">{plan.days.map((d) => <Day key={d.date} day={d} today={today} />)}</div>
    </div>
  );
}

// Cookies y datos sin caché: dentro de Suspense (cacheComponents de Next 16)
export default function SaludPage() {
  return <Suspense fallback={<DashboardSkeleton />}><SaludView /></Suspense>;
}
