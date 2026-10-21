import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/account";

// Portabilidad (RGPD art. 20): descarga de todos los datos de la cuenta
export async function GET() {
  let data: unknown;
  try {
    data = await api<unknown>("/me/export");
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect("/entrar");
    throw e;
  }
  return new Response(JSON.stringify(data, null, 2), {
    headers: { "Content-Type": "application/json", "Content-Disposition": 'attachment; filename="trainsync-datos.json"' },
  });
}
