import Link from "next/link";
import { Suspense } from "react";
import { redirect } from "next/navigation";
import { ReadinessForm, SendHealthToday } from "@/components/HealthActions";
import { DashboardSkeleton } from "@/components/Skeletons";
import { SportIcon } from "@/components/SportIcon";
import { api, ApiError, hasSession, type HealthCardio, type HealthDay, type HealthStrength, type UserPlan } from "@/lib/account";
import { changeDayAction, logSessionAction, moveSessionAction, resetWeekAction } from "@/lib/account-actions";
import { minutes, SPORT_LABEL, WEEKDAY_LABEL } from "@/lib/format";

const GOAL_LABEL: Record<string, string> = { fitness: "Ponerme en forma", strength: "Fuerza y músculo" };

// Sin jerga (Z2, VO2max, umbral…): nombre y cómo saber si vas bien, con la prueba del habla [Práctica: ACSM]
const PLAIN: Record<string, [string, string]> = {
  recovery: ["Suave", "Muy tranquilo: podrías ir cantando. También suma: según la OMS, cualquier actividad es mejor que ninguna."],
  aerobic: ["Ritmo cómodo", "Puedes mantener una conversación sin quedarte sin aire."],
  long: ["Larga a ritmo cómodo", "Más tiempo que otros días, pero siempre pudiendo hablar."],
  threshold: ["Ritmo exigente", "Constante y exigente: solo puedes decir frases cortas."],
  vo2max: ["Series intensas", "En cada serie casi no puedes hablar; entre series, muy suave."],
};

// Minutos suaves (Z1): no cuentan para los 150 de la OMS, pero suman [Verificado: ficha de la OMS sobre actividad física,
// 2024: "any amount of physical activity is better than none; all physical activity counts"]
function lightMinutes(days: HealthDay[]) {
  return Math.round(days.flatMap((d) => d.sessions).filter((s): s is HealthCardio => "blocks" in s)
    .flatMap((s) => s.blocks)
    .reduce((sum, b) => sum + (b.zone === "Z1" ? b.reps * (b.minutes ?? 0) : 0)
      + (b.rest_zone === "Z1" ? b.reps * (b.rest_minutes ?? 0) : 0), 0));
}

function pulse(s: HealthCardio) {
  const zone = s.blocks.find((b) => b.block === "principal")?.zone;
  const target = zone && s.targets[zone];
  return target?.endsWith("ppm") ? `Pulso: ${target.replace("ppm", "pulsaciones por minuto")}` : null;
}

// Hoy, la misma sesión en tus otros deportes: elige la que vas a hacer, envíala al reloj o márcala como hecha
function Alternatives({ s, date }: { s: HealthCardio; date: string }) {
  if (!s.alternatives?.length) return null;
  return (
    <details className="mt-2">
      <summary className="cursor-pointer font-ui text-xs text-accent">¿Hoy prefieres otro deporte?</summary>
      {s.alternatives.map((a) => (
        <div key={a.sport} className="mt-2 space-y-2 rounded-card bg-surface-1 p-2">
          <p className="text-sm">{SPORT_LABEL[a.sport] ?? a.sport} · {PLAIN[a.type]?.[0] ?? a.name} · {minutes(a.duration_min)}</p>
          {pulse(a) && <p className="text-xs text-secondary">{pulse(a)}</p>}
          <div className="flex flex-wrap items-center gap-2">
            <SendHealthToday sport={a.sport} label="Haré esta: enviarla a mi reloj" />
            <form action={logSessionAction}>
              <input type="hidden" name="date" value={date} />
              <input type="hidden" name="sport" value={a.sport} />
              <button className="rounded-full border border-accent/40 px-3 py-1 font-ui text-xs text-accent">Ya la hice</button>
            </form>
          </div>
        </div>
      ))}
    </details>
  );
}

function Session({ s, date, past, done, watch, today }: { s: HealthStrength | HealthCardio; date: string; past: boolean; done: boolean; watch: boolean; today: boolean }) {
  return (
    <article className="rounded-card bg-surface-2 p-3">
      <div className="flex items-center gap-2">
        <SportIcon sport={s.sport} size={18} />
        <h4 className="font-ui text-sm font-medium">{s.sport === "strength" ? s.name : `${SPORT_LABEL[s.sport] ?? s.sport} · ${PLAIN[s.type]?.[0] ?? s.name}`}</h4>
        <span className="ml-auto font-mono text-xs text-secondary">{minutes(s.duration_min)}</span>
      </div>
      {"exercises" in s ? (
        <>
          <ul className="mt-2 space-y-1 text-sm">{s.exercises.map((e) => <li key={e}>{e}</li>)}</ul>
          <p className="mt-2 text-xs text-secondary">{s.notes}</p>
        </>
      ) : (
        <p className="mt-1 text-sm text-secondary">
          {PLAIN[s.type]?.[1] ?? s.goal}{pulse(s) && <><br />{pulse(s)}</>}
        </p>
      )}
      {today && !done && "alternatives" in s && <Alternatives s={s} date={date} />}
      {done && <p className="mt-2 font-ui text-xs text-bio">{watch ? "Hecha · detectada en tu reloj" : "Hecha"}</p>}
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

const small = "rounded-full border border-accent/40 px-3 py-1 font-ui text-xs text-accent";

// Mover, liberar o añadir un día: solo hoy y los días que quedan de la semana
function DayChanges({ day, upcoming }: { day: HealthDay; upcoming: HealthDay[] }) {
  if (day.sessions.length === 0 && day.available_min) {
    return (
      <form action={changeDayAction} className="mt-2">
        <input type="hidden" name="date" value={day.date} />
        <input type="hidden" name="minutes" value={day.available_min} />
        <button className={small}>Prefiero entrenar este día</button>
      </form>
    );
  }
  if (day.sessions.length === 0) {
    return (
      <form action={changeDayAction} className="mt-2 flex items-center gap-2">
        <input type="hidden" name="date" value={day.date} />
        <input type="number" name="minutes" defaultValue={45} min={15} max={240} step={5} aria-label="Minutos disponibles"
          className="w-16 rounded-card bg-surface-2 px-2 py-1 text-sm" />
        <button className={small}>Puedo entrenar este día</button>
      </form>
    );
  }
  const targets = upcoming.filter((d) => d.date !== day.date);
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {targets.length > 0 && (
        <form action={moveSessionAction} className="flex items-center gap-2">
          <input type="hidden" name="from" value={day.date} />
          <select name="to" aria-label="Mover a" className="rounded-card bg-surface-2 px-2 py-1 text-sm">
            {targets.map((d) => <option key={d.date} value={d.date}>{WEEKDAY_LABEL[d.weekday]} {d.date.slice(8)}</option>)}
          </select>
          <button className={small}>Mover</button>
        </form>
      )}
      <form action={changeDayAction}>
        <input type="hidden" name="date" value={day.date} />
        <input type="hidden" name="minutes" value={0} />
        <button className={small}>No puedo este día</button>
      </form>
    </div>
  );
}

function Day({ day, today, upcoming }: { day: HealthDay; today: string; upcoming: HealthDay[] }) {
  const isToday = day.date === today;
  return (
    <section aria-label={`${WEEKDAY_LABEL[day.weekday]} ${day.date}`}
      className={`rounded-module bg-surface-1 p-3 ${isToday ? "ring-1 ring-accent/50" : ""}`}>
      <h3 className="font-ui text-sm text-secondary">{WEEKDAY_LABEL[day.weekday]} {day.date.slice(8)}{isToday && " · hoy"}</h3>
      {day.note && <p className="mt-1 text-sm text-secondary">{day.note}</p>}
      {day.sessions.length === 0 ? <p className="mt-1 text-sm text-secondary">Descanso</p> : (
        <div className="mt-2 space-y-2">
          {day.sessions.map((s) => <Session key={s.sport} s={s} date={day.date} past={day.date <= today} today={day.date === today} done={day.done?.includes(s.sport) ?? false} watch={day.done_from_watch?.includes(s.sport) ?? false} />)}
        </div>
      )}
      {day.matches?.length > 0 && (
        <ul className="mt-2 space-y-1 text-xs text-secondary">
          {day.matches.map((m, i) => <li key={i} className={m.matched ? "" : "text-alert"}>{m.why}</li>)}
        </ul>
      )}
      {day.date >= today && !day.done?.length && <DayChanges day={day} upcoming={upcoming} />}
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
        Esta semana: {plan.equivalent_min} min de actividad y fuerza {plan.dose.strength_days} días
        {lightMinutes(plan.days) > 0 && `, más ${lightMinutes(plan.days)} min suaves que también suman`}
        {deload && " · semana más suave para recuperar"}
      </p>
      {plan.equivalent_min >= 150 && plan.dose.strength_days >= 2 && (
        <p className="font-ui text-sm text-bio">✓ Cumple la recomendación de la OMS para tu salud</p>
      )}
      {plan.changed && (
        <form action={resetWeekAction} className="flex items-center gap-3 text-sm">
          <span className="text-secondary">Semana modificada: el resto se ha recalculado.</span>
          <button className="font-ui text-accent">Volver al plan original</button>
        </form>
      )}
      {plan.warnings.map((w) => <p key={w} className="rounded-card border-l-2 border-alert bg-surface-1 p-3 text-sm">{w}</p>)}
      {plan.info?.map((i) => <p key={i} className="text-sm text-secondary">{i}</p>)}
      <ReadinessForm />
      <SendHealthToday />
      <div className="space-y-3">
        {plan.days.map((d) => <Day key={d.date} day={d} today={today} upcoming={plan.days.filter((x) => x.date >= today)} />)}
      </div>
    </div>
  );
}

// Cookies y datos sin caché: dentro de Suspense (cacheComponents de Next 16)
export default function SaludPage() {
  return <Suspense fallback={<DashboardSkeleton />}><SaludView /></Suspense>;
}
