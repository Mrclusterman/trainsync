"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

const TABS = [
  { href: "/", label: "Hoy", icon: <path d="M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1V10z" /> },
  { href: "/plan", label: "Plan", icon: <path d="M4 5h16v16H4zM4 9h16M8 3v4M16 3v4" /> },
  { href: "/salud", label: "Salud", icon: <path d="M12 21s-7-4.5-9-9.5C1.6 8 3.6 4 7.5 4c2 0 3.5 1.2 4.5 2.6C13 5.2 14.5 4 16.5 4 20.4 4 22.4 8 21 11.5 19 16.5 12 21 12 21z" /> },
];

// Barra inferior glass: se contrae al bajar (solo iconos) y se expande al subir.
// shortcut: transición CSS en lugar de física de muelle (stiffness 400, damping 30); usar una librería de animación si se añade móvil nativo
export function BottomNav() {
  const pathname = usePathname();
  const [compact, setCompact] = useState(false);

  useEffect(() => {
    let last = window.scrollY;
    const onScroll = () => {
      setCompact(window.scrollY > last && window.scrollY > 40);
      last = window.scrollY;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <nav aria-label="Principal"
      className="glass-surface fixed inset-x-4 bottom-4 z-20 mx-auto max-w-xs rounded-full px-2 transition-all duration-200">
      <ul className="flex justify-around">
        {TABS.map((t) => {
          const active = t.href === "/" ? pathname === "/" : pathname.startsWith(t.href);
          return (
            <li key={t.href}>
              <Link href={t.href} aria-current={active ? "page" : undefined}
                className={`flex flex-col items-center px-5 font-ui text-xs transition-all duration-200 ${compact ? "py-2" : "py-2.5"} ${active ? "text-accent" : "text-secondary"}`}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}
                  strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{t.icon}</svg>
                <span className={compact ? "sr-only" : "mt-0.5"}>{t.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
