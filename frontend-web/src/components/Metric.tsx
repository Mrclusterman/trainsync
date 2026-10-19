import { CountUp } from "./CountUp";

export function MetricCard({ label, value, decimals = 0, unit, color, note, glow = false }: {
  label: string;
  value: number | null;
  decimals?: number;
  unit?: string;
  color: string;
  note: string;
  glow?: boolean;
}) {
  return (
    <div className={`rounded-card bg-surface-1 p-4 ${glow ? "metric-glow" : ""}`}>
      <p className="font-ui text-xs uppercase tracking-wide text-secondary">{label}</p>
      <p className="mt-1 font-mono text-5xl leading-none" style={{ color }}>
        {value === null ? "—" : <CountUp value={value} decimals={decimals} />}
        {unit && value !== null && <span className="ml-1 text-base text-secondary">{unit}</span>}
      </p>
      <p className="mt-2 font-ui text-xs" style={{ color }}>{note}</p>
    </div>
  );
}
