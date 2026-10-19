"use client";

import { useEffect, useState } from "react";

// Count-up de 0 al valor en 800 ms ease-out (diseño); sin animación si el usuario pide menos movimiento
export function CountUp({ value, decimals = 0 }: { value: number; decimals?: number }) {
  const [shown, setShown] = useState(value);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let frame = 0;
    let start: number | null = null;
    const tick = (now: number) => {
      start ??= now; // el timestamp del frame puede ser anterior a performance.now(): t negativo daba valores < 0
      const t = Math.min((now - start) / 800, 1);
      setShown(value * (1 - (1 - t) ** 3));
      if (t < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [value]);

  return <>{shown.toFixed(decimals)}</>;
}
