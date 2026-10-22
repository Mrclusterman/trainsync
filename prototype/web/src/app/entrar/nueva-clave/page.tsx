import Link from "next/link";
import { Suspense } from "react";
import { ResetForm } from "@/components/AuthForms";

async function NuevaClave({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  if (!token) return <p className="text-sm text-alert">Falta el enlace. <Link href="/entrar/recuperar" className="text-accent">Pide uno nuevo</Link>.</p>;
  return <ResetForm token={token} />;
}

export default function NuevaClavePage(props: { searchParams: Promise<{ token?: string }> }) {
  return (
    <div className="space-y-4 pt-6">
      <h1 className="font-ui text-2xl font-bold">Contraseña nueva</h1>
      <p className="text-sm text-secondary">Al cambiarla se cierran las sesiones abiertas en otros dispositivos.</p>
      <Suspense fallback={null}><NuevaClave {...props} /></Suspense>
    </div>
  );
}
