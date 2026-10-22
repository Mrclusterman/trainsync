"use client";

import { usePathname } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";

const subscribe = (notify: () => void) => {
  window.addEventListener("online", notify);
  window.addEventListener("offline", notify);
  return () => { window.removeEventListener("online", notify); window.removeEventListener("offline", notify); };
};

// Registra el service worker (public/sw.js) y avisa cuando lo que se ve es la última copia guardada
export function Offline() {
  const online = useSyncExternalStore(subscribe, () => navigator.onLine, () => true);
  const path = usePathname();
  useEffect(() => {  // cerrar sesión o borrar la cuenta lleva a /entrar sin recargar: el SW no lo ve
    if (path.startsWith("/entrar") && "caches" in window) caches.delete("trainsync-pages-v1").catch(() => {});
  }, [path]);
  useEffect(() => {
    if (process.env.NODE_ENV === "production" && "serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js", { scope: "/", updateViaCache: "none" }).catch(() => {});
    }
  }, []);
  if (online) return null;
  return (
    <p role="status" className="sticky top-0 z-10 -mx-4 bg-surface-2 px-4 py-2 text-center text-sm text-alert">
      Sin conexión: ves la última versión guardada. Para marcar o cambiar sesiones hace falta conexión.
    </p>
  );
}
