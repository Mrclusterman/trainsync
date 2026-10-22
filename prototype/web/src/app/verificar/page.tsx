import Link from "next/link";
import { Suspense } from "react";
import { api, ApiError } from "@/lib/account";

async function Verificar({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  try {
    if (!token) throw new ApiError(400, "Falta el enlace");
    await api("/auth/verify", { method: "POST", body: JSON.stringify({ token }) });
  } catch (e) {
    return (
      <p className="text-sm text-alert">
        {e instanceof ApiError ? e.message : "No se pudo confirmar"}. Puedes pedir otro desde{" "}
        <Link href="/salud/perfil" className="text-accent">tu perfil</Link>.
      </p>
    );
  }
  return <p className="text-sm">Email confirmado. <Link href="/salud" className="text-accent">Ir a tu plan</Link></p>;
}

export default function VerificarPage(props: { searchParams: Promise<{ token?: string }> }) {
  return (
    <div className="space-y-4 pt-6">
      <h1 className="font-ui text-2xl font-bold">Confirmar email</h1>
      <Suspense fallback={<p className="text-sm text-secondary">Confirmando…</p>}><Verificar {...props} /></Suspense>
    </div>
  );
}
