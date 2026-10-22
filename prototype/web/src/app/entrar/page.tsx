import { AuthForms } from "@/components/AuthForms";

export default function EntrarPage() {
  return (
    <div className="space-y-4 pt-6">
      <h1 className="font-ui text-2xl font-bold">Tu plan de salud</h1>
      <p className="text-sm text-secondary">
        Un plan semanal para ponerte en forma o ganar fuerza y músculo, con o sin reloj. Gratis y de código abierto.
      </p>
      <AuthForms />
    </div>
  );
}
