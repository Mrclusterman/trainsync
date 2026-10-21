"use client";

import { useActionState, useState } from "react";
import { loginAction, registerAction, type FormState } from "@/lib/account-actions";

const field = "mt-1 w-full rounded-card bg-surface-2 px-3 py-2 text-primary";
const button = "mt-4 w-full rounded-full bg-accent px-5 py-2.5 font-ui text-sm font-medium text-canvas disabled:opacity-60";

function Error({ state }: { state: FormState }) {
  return state?.error ? <p role="alert" className="mt-3 text-sm text-alert">{state.error}</p> : null;
}

export function AuthForms() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [loginState, login, loggingIn] = useActionState(loginAction, null);
  const [registerState, register, registering] = useActionState(registerAction, null);
  return (
    <div className="rounded-module bg-surface-1 p-4">
      <div role="tablist" className="flex gap-2 font-ui text-sm">
        {(["login", "register"] as const).map((m) => (
          <button key={m} role="tab" type="button" aria-selected={mode === m} onClick={() => setMode(m)}
            className={`rounded-full px-4 py-1.5 ${mode === m ? "bg-accent text-canvas" : "text-secondary"}`}>
            {m === "login" ? "Entrar" : "Crear cuenta"}
          </button>
        ))}
      </div>
      {mode === "login" ? (
        <form action={login} className="mt-4">
          <label className="block text-sm">Email<input name="email" type="email" required autoComplete="email" className={field} /></label>
          <label className="mt-3 block text-sm">Contraseña
            <input name="password" type="password" required autoComplete="current-password" className={field} /></label>
          <Error state={loginState} />
          <button disabled={loggingIn} className={button}>{loggingIn ? "Entrando…" : "Entrar"}</button>
        </form>
      ) : (
        <form action={register} className="mt-4">
          <label className="block text-sm">Email<input name="email" type="email" required autoComplete="email" className={field} /></label>
          <label className="mt-3 block text-sm">Contraseña (10 caracteres o más)
            <input name="password" type="password" required minLength={10} autoComplete="new-password" className={field} /></label>
          <label className="mt-3 block text-sm">Año de nacimiento
            <input name="birth_year" type="number" required min={1900} max={new Date().getFullYear()} className={field} /></label>
          {/* Casillas sin marcar y consentimiento de salud separado de los términos (CUMPLIMIENTO #6) */}
          <label className="mt-4 flex gap-2 text-sm">
            <input name="accept_terms" type="checkbox" required className="mt-1" />
            <span>Acepto los términos de uso. TrainSync no es consejo médico ni un producto sanitario.</span>
          </label>
          <label className="mt-3 flex gap-2 text-sm">
            <input name="health_consent" type="checkbox" required className="mt-1" />
            <span>Doy mi consentimiento explícito para que TrainSync trate mis datos de salud (actividad, frecuencia
              cardíaca, sueño y respuestas del cribado) solo para crear mi plan. Puedo retirarlo borrando mi cuenta.</span>
          </label>
          <Error state={registerState} />
          <button disabled={registering} className={button}>{registering ? "Creando…" : "Crear cuenta"}</button>
        </form>
      )}
    </div>
  );
}
