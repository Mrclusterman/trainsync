// Motivo del motor con su etiqueta de evidencia al final ("… [Verificado]"), como en docs/MOTOR_ENTRENAMIENTO.md
const EVIDENCE: Record<string, string> = {
  Verificado: "Lo respaldan estudios publicados.",
  Práctica: "Práctica habitual de entrenadores, sin un ensayo que la compare con otras.",
  Inferencia: "Regla propia razonada a partir de las fuentes; aún sin comprobar directamente.",
};

export function Reason({ text }: { text: string }) {
  const [, body, tag] = text.match(/^(.*?)\s*\[(Verificado|Práctica|Inferencia)\]$/) ?? [, text, undefined];
  return (
    <div className="border-l-2 border-alert pl-3 text-sm">
      {body}
      {tag && (
        <details className="mt-1 text-xs text-secondary">
          <summary className="inline-block cursor-pointer rounded-card bg-surface-2 px-2 py-0.5 font-ui">{tag}</summary>
          <p className="mt-1">{EVIDENCE[tag]} Fuentes en la documentación del motor.</p>
        </details>
      )}
    </div>
  );
}
