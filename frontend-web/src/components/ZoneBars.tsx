const COLORS = ["--zone-1", "--zone-2", "--zone-3", "--zone-4", "--zone-5", "--zone-6"];

// Distribución de zonas de una actividad (diseño: barras de 6 px; Z3 > 15 % en sesión fácil = aviso + shake una vez)
export function ZoneBars({ secs, grayZone }: { secs: number[]; grayZone: boolean }) {
  const total = secs.reduce((a, b) => a + b, 0);
  if (!total) return <p className="text-sm text-secondary">Sin datos de frecuencia cardíaca.</p>;
  return (
    <ul className="space-y-2">
      {secs.map((s, i) => {
        const pct = (s / total) * 100;
        const color = `var(${COLORS[Math.min(i, 5)]})`;
        const warn = grayZone && i === 2;
        return (
          <li key={i} className={`flex items-center gap-3 ${warn ? "shake-once" : ""}`}>
            <span className="w-7 font-mono text-xs text-secondary">Z{i + 1}</span>
            <span className="h-1.5 flex-1 rounded-[3px] bg-surface-2">
              <span className="block h-full rounded-[3px]"
                style={{ width: `${pct}%`, background: color, boxShadow: `0 0 8px ${color}` }} />
            </span>
            <span className="w-10 text-right font-mono text-xs">{Math.round(pct)}%</span>
            {warn && (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-alert)" strokeWidth={1.5}
                aria-label="Zona gris" role="img">
                <path d="M12 3l9.5 17h-19L12 3zM12 10v4M12 17v.5" />
              </svg>
            )}
          </li>
        );
      })}
    </ul>
  );
}
