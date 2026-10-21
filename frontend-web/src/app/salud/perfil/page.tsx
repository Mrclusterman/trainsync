import { redirect } from "next/navigation";
import { Suspense } from "react";
import { IntervalsLinkForm } from "@/components/IntervalsLinkForm";
import { ProfileForm, type SavedProfile } from "@/components/ProfileForm";
import { api, ApiError, hasSession, type Questions } from "@/lib/account";
import { deleteAccountAction, logoutAction } from "@/lib/account-actions";

type Export = { profile: { plan: Omit<NonNullable<SavedProfile>, "screening">; screening: NonNullable<SavedProfile>["screening"] } | null;
  intervals: { athlete_id: string } | null };

async function PerfilView() {
  if (!(await hasSession())) redirect("/entrar");
  let data: Export;
  try {
    data = await api<Export>("/me/export");
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect("/entrar");
    throw e;
  }
  const questions = await api<Questions>("/screening/questions");
  const saved = data.profile ? { ...data.profile.plan, screening: data.profile.screening } : null;
  return (
    <div className="space-y-6 pt-4">
      <h1 className="font-ui text-2xl font-bold">Tu perfil</h1>
      <ProfileForm questions={questions} saved={saved} />

      <section aria-labelledby="reloj" className="rounded-module bg-surface-1 p-4">
        <h2 id="reloj" className="font-ui text-sm text-secondary">Reloj (intervals.icu)</h2>
        <div className="mt-2"><IntervalsLinkForm linked={Boolean(data.intervals)} /></div>
      </section>

      <section aria-labelledby="cuenta" className="space-y-3 rounded-module bg-surface-1 p-4">
        <h2 id="cuenta" className="font-ui text-sm text-secondary">Tu cuenta y tus datos</h2>
        <a href="/salud/exportar" className="block text-sm text-accent">Descargar todos mis datos (JSON)</a>
        <form action={logoutAction}><button className="text-sm text-accent">Cerrar sesión</button></form>
        <form action={deleteAccountAction} className="space-y-2">
          <label className="block text-sm">Para borrar tu cuenta y todos tus datos, escribe BORRAR:
            <input name="confirm" required pattern="BORRAR" className="mt-1 w-full rounded-card bg-surface-2 px-3 py-2" /></label>
          <button className="rounded-full border border-danger px-4 py-2 font-ui text-sm text-danger">Borrar mi cuenta</button>
        </form>
      </section>
    </div>
  );
}

export default function PerfilPage() {
  return <Suspense fallback={<p className="pt-4 text-sm text-secondary">Cargando…</p>}><PerfilView /></Suspense>;
}
