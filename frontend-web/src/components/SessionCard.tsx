import type { Block, Session, Strength, Zone } from "@/lib/api";
import { minutes, SPORT_LABEL, ZONE_COLOR } from "@/lib/format";
import { SportIcon } from "./SportIcon";

function Pill({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-full border border-accent/25 bg-accent/12 px-2.5 py-1 font-mono text-xs text-primary">
      {children}
    </span>
  );
}

export function ZoneChip({ zone }: { zone: Zone }) {
  return (
    <span className="rounded-chip px-1.5 py-0.5 font-mono text-xs font-medium text-canvas"
      style={{ background: ZONE_COLOR[zone] }}>
      {zone}
    </span>
  );
}

function structure(b: Block): string {
  const work = minutes(b.minutes);
  if (b.reps === 1) return work;
  return b.rest_minutes ? `${b.reps} × ${work} / ${minutes(b.rest_minutes)}` : `${b.reps} × ${work}`;
}

function topZone(s: Session): Zone {
  return s.blocks.map((b) => b.zone).sort().at(-1) ?? "Z2";
}

function BlockRow({ block, target }: { block: Block; target?: string }) {
  return (
    <li className="border-t border-surface-3 py-2.5 first:border-t-0">
      <div className="flex items-center justify-between gap-3">
        <span className="font-ui text-sm text-primary first-letter:uppercase">{block.block}</span>
        <span className="flex items-center gap-2">
          <span className="font-mono text-sm">{structure(block)}</span>
          <ZoneChip zone={block.zone} />
        </span>
      </div>
      <div className="mt-1 flex flex-wrap gap-x-3 font-mono text-xs text-secondary">
        {target && <span>{target}</span>}
        <span>RPE {block.rpe}</span>
      </div>
      {block.detail && <p className="mt-1 text-sm text-secondary">{block.detail}</p>}
      {block.guide && <p className="mt-1 text-sm text-alert">{block.guide}</p>}
    </li>
  );
}

export function SessionCard({ session, compact = false }: { session: Session; compact?: boolean }) {
  return (
    <article className="rounded-module bg-surface-1 p-4">
      <header className="flex items-center gap-3">
        <span className="grid size-10 place-items-center rounded-card bg-surface-2 text-accent">
          <SportIcon sport={session.sport} />
        </span>
        <div>
          <p className="font-ui text-xs uppercase tracking-wide text-secondary">{SPORT_LABEL[session.sport]}</p>
          <h3 className="font-ui text-xl font-bold leading-tight">{session.name}</h3>
        </div>
      </header>

      <div className="mt-3 flex flex-wrap gap-2">
        <Pill>{minutes(session.duration_min)}</Pill>
        <Pill>{topZone(session)}</Pill>
        <Pill>{session.tss} TSS</Pill>
        {session.hr_cap && <Pill>techo {session.hr_cap} ppm</Pill>}
      </div>

      <p className="mt-3 text-sm text-secondary">{session.goal}</p>

      <details className="group mt-3" open={!compact}>
        <summary className="cursor-pointer font-ui text-sm text-accent">Ver estructura</summary>
        <ul className="mt-2">
          {session.blocks.map((b, i) => (
            <BlockRow key={i} block={b} target={session.targets[b.zone]} />
          ))}
        </ul>
        <div className="mt-3 space-y-2 text-sm">
          <p className="border-l-2 border-alert pl-3 text-secondary">{session.if_bad}</p>
          <p className="border-l-2 border-accent pl-3 text-secondary">{session.if_good}</p>
        </div>
      </details>
    </article>
  );
}

export function StrengthCard({ strength }: { strength: Strength }) {
  return (
    <article className="rounded-module bg-surface-1 p-4">
      <header className="flex items-center gap-3">
        <span className="grid size-10 place-items-center rounded-card bg-surface-2 text-accent">
          <SportIcon sport="strength" />
        </span>
        <div>
          <p className="font-ui text-xs uppercase tracking-wide text-secondary">Complemento</p>
          <h3 className="font-ui text-xl font-bold leading-tight">{strength.name}</h3>
        </div>
      </header>
      <div className="mt-3 flex flex-wrap gap-2">
        <Pill>{minutes(strength.duration_min)}</Pill>
        <Pill>RPE {strength.rpe_planned}</Pill>
        <Pill>{strength.tss} TSS</Pill>
      </div>
      <p className="mt-3 text-sm">{strength.dose}</p>
      <p className="mt-1 text-sm text-secondary">Hazla {strength.order}.</p>
      <details className="mt-3">
        <summary className="cursor-pointer font-ui text-sm text-accent">Ejercicios</summary>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-secondary">
          {strength.exercises.map((e) => <li key={e}>{e}</li>)}
        </ul>
      </details>
    </article>
  );
}

export function Alternatives({ sessions }: { sessions: Session[] }) {
  if (!sessions.length) return null;
  return (
    <section aria-label="Alternativas" className="space-y-2">
      <h3 className="font-ui text-sm text-secondary">Alternativas con la misma carga</h3>
      {sessions.map((s) => (
        <details key={s.id + s.sport} className="rounded-card bg-surface-1">
          <summary className="flex cursor-pointer items-center gap-3 p-3">
            <SportIcon sport={s.sport} className="text-secondary" />
            <span className="font-ui text-sm">{SPORT_LABEL[s.sport]} · {s.name}</span>
            <span className="ml-auto font-mono text-xs text-secondary">{minutes(s.duration_min)} · {s.tss} TSS</span>
          </summary>
          <div className="px-1 pb-1"><SessionCard session={s} /></div>
        </details>
      ))}
    </section>
  );
}
