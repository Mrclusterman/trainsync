import Link from "next/link";
import { Suspense } from "react";
import { MetricCard } from "@/components/Metric";
import { Alternatives, SessionCard, StrengthCard } from "@/components/SessionCard";
import { SendToWatch } from "@/components/SendToWatch";
import { DashboardSkeleton } from "@/components/Skeletons";
import { SportIcon } from "@/components/SportIcon";
import { ZoneBars } from "@/components/ZoneBars";
import { redirect } from "next/navigation";
import { LinkIntervals } from "@/components/LinkIntervals";
import { ApiError, hasSession } from "@/lib/account";
import { getPlan, getState, type Plan, type State } from "@/lib/api";
import { athleteMood, minutes, PHASE_LABEL, shortDate, SPORT_LABEL, tsbTone, WEEKDAY_LABEL } from "@/lib/format";
import { Reason } from "@/components/Reason";

const MOOD = {
  optimal: { dot: "var(--color-bio)", border: "rgba(0, 229, 160, 0.35)", text: "Condiciones óptimas para calidad" },
  normal: { dot: "var(--color-accent)", border: "transparent", text: null },
  fatigued: { dot: "var(--color-alert)", border: "rgba(255, 140, 66, 0.2)", text: "Hoy es día de recuperación. Es parte del plan." },
};

async function Dashboard() {
  if (!(await hasSession())) redirect("/entrar");
  let state: State, plan: Plan;
  try {
    [state, plan] = await Promise.all([getState(), getPlan(14)]);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect("/entrar");
    if (e instanceof ApiError && e.status === 409) return <LinkIntervals />;
    throw e;
  }
  const [today, ...rest] = plan.days;
  const mood = MOOD[athleteMood(state, today)];
  const tsb = tsbTone(state.tsb);
  const hrvOk = state.hrv_status === "within_swc";
  const sleep = state.sleep_score;

  return (
    <div className="space-y-6">
      <header className="glass-surface sticky top-0 z-10 -mx-4 border-t-2 px-4 py-3" style={{ borderTopColor: mood.border }}>
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-full" style={{ background: mood.dot }} aria-hidden="true" />
          <h1 className="font-ui text-lg font-bold first-letter:uppercase">
            {new Date(`${state.date}T12:00:00`).toLocaleDateString("es-ES", { weekday: "long", day: "numeric", month: "long" })}
          </h1>
          <span className="ml-auto font-ui text-xs text-secondary">intervals.icu</span>
        </div>
        <p className="mt-0.5 font-ui text-xs text-secondary">
          {PHASE_LABEL[state.phase]}
          {state.goal && ` · ${state.goal.name} (${state.goal.priority}) · ${shortDate(state.goal.date)}`}
          {state.recovery_week && " · semana de descarga"}
        </p>
        {mood.text && <p className="mt-1 font-ui text-xs" style={{ color: mood.dot }}>{mood.text}</p>}
      </header>

      {state.warnings.length > 0 && (
        <section aria-label="Avisos" className="space-y-2">
          {state.warnings.map((w) => (
            <p key={w} className="rounded-card border-l-2 border-alert bg-surface-1 p-3 text-sm">{w}</p>
          ))}
        </section>
      )}

      <section aria-label="Estado de forma" className="grid grid-cols-2 gap-3">
        <MetricCard label="Forma · TSB" value={state.tsb} decimals={1} color={tsb.color} note={tsb.label} glow />
        <MetricCard label="Fitness · CTL" value={state.ctl} decimals={1} color="var(--color-text-primary)"
          note={`Fatiga ATL ${state.atl.toFixed(1)}`} />
        <MetricCard label="HRV" value={state.hrv} unit="ms"
          color={state.hrv_status ? (hrvOk ? "var(--color-bio)" : "var(--color-alert)") : "var(--color-text-secondary)"}
          note={state.hrv_status ? (hrvOk ? "En tu rango normal" : "Fuera de tu rango") : "Sin datos suficientes"} />
        <MetricCard label="Sueño" value={sleep} unit="/100"
          color={sleep === null ? "var(--color-text-secondary)" : sleep >= 75 ? "var(--color-bio)" : sleep >= 50 ? "var(--color-text-primary)" : "var(--color-alert)"}
          note={sleep === null ? "Sin datos" : sleep < 50 ? "Bajo: -30 % de carga" : "Sin ajuste"} />
      </section>

      <section aria-labelledby="hoy" className="space-y-3">
        <h2 id="hoy" className="font-ui text-sm text-secondary">Sesión de hoy</h2>
        {today.reasons.map((r) => <Reason key={r} text={r} />)}
        {today.main && <SendToWatch />}
        {today.main ? <SessionCard session={today.main} /> : (
          <p className="rounded-module bg-surface-1 p-4 text-secondary">Descanso. Recuperar también es entrenar.</p>
        )}
        {today.strength && <StrengthCard strength={today.strength} />}
        <Alternatives sessions={today.alternatives} />
      </section>

      <section aria-labelledby="proximos">
        <div className="flex items-baseline justify-between">
          <h2 id="proximos" className="font-ui text-sm text-secondary">Próximos 3 días</h2>
          <Link href="/plan" className="font-ui text-sm text-accent">Plan completo</Link>
        </div>
        <ul className="no-scrollbar -mx-4 mt-2 flex gap-2 overflow-x-auto px-4">
          {rest.slice(0, 3).map((d) => (
            <li key={d.date} className="w-36 shrink-0 rounded-card bg-surface-1 p-3">
              <p className="font-ui text-xs text-secondary">{WEEKDAY_LABEL[d.weekday]} · {shortDate(d.date)}</p>
              {d.main ? (
                <>
                  <SportIcon sport={d.main.sport} className="mt-2 text-accent" />
                  <p className="mt-1 font-ui text-sm leading-tight">{d.main.name}</p>
                  <p className="font-mono text-xs text-secondary">{minutes(d.main.duration_min)} · {d.main.tss} TSS</p>
                </>
              ) : <p className="mt-2 font-ui text-sm text-secondary">Descanso</p>}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="ultima" className="rounded-module bg-surface-1 p-4">
        <h2 id="ultima" className="font-ui text-sm text-secondary">Última actividad</h2>
        {state.last_activity ? (
          <>
            <p className="mt-1 font-ui">{state.last_activity.name ?? SPORT_LABEL[state.last_activity.sport]}</p>
            <p className="font-mono text-xs text-secondary">
              {shortDate(state.last_activity.date)} · {minutes(state.last_activity.duration_min)}
              {state.last_activity.tss !== null && ` · ${Math.round(state.last_activity.tss)} TSS`}
            </p>
            {state.last_activity.gray_zone && (
              <p className="mt-2 text-sm text-alert">Esta sesión entró en zona gris: Z3 por encima del 15 %.</p>
            )}
            <div className="mt-3"><ZoneBars secs={state.last_activity.hr_zone_secs} grayZone={state.last_activity.gray_zone} /></div>
          </>
        ) : <p className="mt-1 text-sm text-secondary">Sin actividades en los últimos 7 días.</p>}
      </section>
    </div>
  );
}

export default function Home() {
  return (
    <Suspense fallback={<div className="pt-4"><DashboardSkeleton /></div>}>
      <Dashboard />
    </Suspense>
  );
}
