import Link from "next/link";

// El plan de carrera necesita los datos del reloj del propio usuario
export function LinkIntervals() {
  return (
    <section className="space-y-3 pt-6">
      <h2 className="font-ui text-2xl font-bold">Plan de carrera</h2>
      <p className="rounded-module bg-surface-1 p-4 text-sm text-secondary">
        Este plan se calcula con los datos de tu reloj (forma, HRV, sueño y tus carreras). Vincula tu cuenta de
        intervals.icu para verlo. Si no tienes reloj, tu plan está en Salud.
      </p>
      <div className="flex gap-4 font-ui text-sm">
        <Link href="/salud/perfil" className="text-accent">Vincular intervals.icu</Link>
        <Link href="/salud" className="text-accent">Ir a Salud</Link>
      </div>
    </section>
  );
}
