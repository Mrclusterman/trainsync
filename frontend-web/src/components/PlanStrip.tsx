"use client";

import { useRef, useState } from "react";
import type { Day } from "@/lib/api";
import { minutes, PHASE_LABEL, shortDate, WEEKDAY_LABEL } from "@/lib/format";
import { Alternatives, SessionCard, StrengthCard } from "./SessionCard";
import { SportIcon } from "./SportIcon";
import { Reason } from "./Reason";

const dayTss = (d: Day) => (d.main?.tss ?? 0) + (d.strength?.tss ?? 0);

export function PlanStrip({ days }: { days: Day[] }) {
  const sheet = useRef<HTMLDialogElement>(null);
  const [selected, setSelected] = useState<Day | null>(null);
  const open = (d: Day) => {
    setSelected(d);
    sheet.current?.showModal();
  };
  const weeks = [days.slice(0, 7), days.slice(7, 14)].filter((w) => w.length);
  const maxWeek = Math.max(...weeks.map((w) => w.reduce((a, d) => a + dayTss(d), 0)), 1);

  return (
    <>
      <ol className="no-scrollbar -mx-4 flex snap-x gap-2 overflow-x-auto px-4 pb-2">
        {days.map((d, i) => (
          <li key={d.date} className="snap-start">
            <button onClick={() => open(d)}
              className={`flex h-36 w-24 flex-col rounded-card bg-surface-1 p-3 text-left transition-transform active:scale-[1.02] ${i === 0 ? "border border-accent" : ""}`}>
              <span className="font-ui text-xs text-secondary">{WEEKDAY_LABEL[d.weekday]} · {shortDate(d.date)}</span>
              {d.main ? (
                <>
                  <SportIcon sport={d.main.sport} className="mt-2 text-accent" />
                  <span className="mt-1 font-ui text-sm leading-tight">{d.main.name}</span>
                  <span className="mt-auto font-mono text-xs text-secondary">{minutes(d.main.duration_min)}</span>
                </>
              ) : (
                <span className="mt-2 font-ui text-sm text-secondary">Descanso</span>
              )}
              {d.strength && <span className="font-mono text-xs text-secondary">+ fuerza</span>}
            </button>
          </li>
        ))}
      </ol>

      <section aria-label="TSS planificado por semana" className="mt-6 space-y-3 rounded-module bg-surface-1 p-4">
        <h2 className="font-ui text-sm text-secondary">TSS planificado</h2>
        {weeks.map((w, i) => {
          const tss = w.reduce((a, d) => a + dayTss(d), 0);
          return (
            <div key={i} className="flex items-center gap-3">
              <span className="w-20 font-ui text-xs text-secondary">Semana {i + 1}</span>
              <span className="h-1.5 flex-1 rounded-[3px] bg-surface-2">
                <span className="block h-full rounded-[3px] bg-accent" style={{ width: `${(tss / maxWeek) * 100}%` }} />
              </span>
              <span className="w-12 text-right font-mono text-sm">{tss}</span>
            </div>
          );
        })}
      </section>

      <dialog ref={sheet} onClose={() => setSelected(null)} aria-label="Sesión del día"
        className="glass-surface fixed inset-x-0 bottom-0 top-auto m-0 mx-auto max-h-[85vh] w-full max-w-xl overflow-y-auto rounded-t-modal p-4 text-primary backdrop:bg-black/60">
        {selected && (
          <div className="space-y-3 pb-4">
            <div className="flex items-center justify-between">
              <p className="font-ui text-sm text-secondary">
                {WEEKDAY_LABEL[selected.weekday]} {shortDate(selected.date)} · {PHASE_LABEL[selected.phase]}
              </p>
              <button onClick={() => sheet.current?.close()} className="font-ui text-sm text-accent">Cerrar</button>
            </div>
            {selected.reasons.map((r) => <Reason key={r} text={r} />)}
            {selected.main ? <SessionCard session={selected.main} /> : <p className="text-secondary">Descanso: forma parte del plan.</p>}
            {selected.strength && <StrengthCard strength={selected.strength} />}
            <Alternatives sessions={selected.alternatives} />
          </div>
        )}
      </dialog>
    </>
  );
}
