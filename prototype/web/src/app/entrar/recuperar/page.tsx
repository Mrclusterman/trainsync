import { ForgotForm } from "@/components/AuthForms";

export default function RecuperarPage() {
  return (
    <div className="space-y-4 pt-6">
      <h1 className="font-ui text-2xl font-bold">Recuperar la contraseña</h1>
      <p className="text-sm text-secondary">Te enviamos un enlace al email de tu cuenta para elegir una contraseña nueva.</p>
      <ForgotForm />
    </div>
  );
}
