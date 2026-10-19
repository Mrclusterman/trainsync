"use client";

export default function ErrorPage({ error, retry }: { error: unknown; retry: () => void }) {
  // En producción Next oculta el mensaje del servidor; en desarrollo se muestra para depurar
  const detail = process.env.NODE_ENV === "development" && error instanceof Error ? error.message : null;
  return (
    <div role="alert" className="mt-10 rounded-module bg-surface-1 p-5">
      <h1 className="font-ui text-xl font-bold">No se pudieron cargar tus datos</h1>
      <p className="mt-2 text-sm text-secondary">
        Comprueba que el backend está arrancado y que la API key de intervals.icu es válida.
      </p>
      {detail && <p className="mt-2 font-mono text-xs text-alert">{detail}</p>}
      <button onClick={() => retry()} className="mt-4 rounded-full bg-accent px-5 py-2 font-ui text-sm text-primary">
        Reintentar
      </button>
    </div>
  );
}
