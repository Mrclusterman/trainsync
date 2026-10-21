// Iconos de deporte SVG propios (diseño: nunca emoji), trazo 1.5 como Lucide
const PATHS: Record<string, React.ReactNode> = {
  running: (
    <>
      <circle cx="15" cy="4" r="2" />
      <path d="M8 21l3.5-6 3 2.5V22M11.5 15l1.5-5.5 4 2.5 2.5-1M13 9.5L9 9l-3 3" />
    </>
  ),
  cycling: (
    <>
      <circle cx="5.5" cy="17" r="3.5" />
      <circle cx="18.5" cy="17" r="3.5" />
      <path d="M5.5 17l4-8h6l3 8M9.5 9l3.5 8M14 6h3" />
    </>
  ),
  swimming_pool: (
    <>
      <circle cx="17" cy="6" r="2" />
      <path d="M3 13l5-4 4 3M2 17c2 0 2-1.5 4-1.5S8 17 10 17s2-1.5 4-1.5S16 17 18 17s2-1.5 4-1.5" />
    </>
  ),
  swimming_open: (
    <path d="M2 9c2 0 2-1.5 4-1.5S8 9 10 9s2-1.5 4-1.5S16 9 18 9s2-1.5 4-1.5M2 15c2 0 2-1.5 4-1.5s2 1.5 4 1.5 2-1.5 4-1.5 2 1.5 4 1.5 2-1.5 4-1.5" />
  ),
  strength: <path d="M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10" />,
  walking: (
    <>
      <circle cx="13" cy="4" r="2" />
      <path d="M9 21l2-6 2 2v4M11 15l1-5 3 3 2 0M12 10l-3 2-1 3" />
    </>
  ),
};

export function SportIcon({ sport, size = 22, className }: { sport: string; size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      {PATHS[sport] ?? PATHS.running}
    </svg>
  );
}
