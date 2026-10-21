import type { Metadata, Viewport } from "next";
import { DM_Mono, Geist, Instrument_Sans } from "next/font/google";
import { BottomNav } from "@/components/BottomNav";
import "./globals.css";

// Tipografía del diseño: DM Mono (métricas), Geist (interfaz), Instrument Sans (cuerpo)
const dmMono = DM_Mono({ variable: "--font-dm-mono", subsets: ["latin"], weight: ["400", "500"] });
const geist = Geist({ variable: "--font-geist", subsets: ["latin"] });
const instrument = Instrument_Sans({ variable: "--font-instrument", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "TrainSync",
  description: "Planes de entrenamiento de resistencia gratuitos sobre tus datos de intervals.icu",
  // La app ya es oscura: Dark Reader la dejaba igual pero alteraba los SVG y rompía la hidratación de React
  other: { "darkreader-lock": "true" },
};

export const viewport: Viewport = { themeColor: "#0a0a0f", colorScheme: "dark" };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es" className={`${dmMono.variable} ${geist.variable} ${instrument.variable} h-full antialiased`}>
      <body className="min-h-full">
        <main className="mx-auto max-w-xl px-4 pb-28">
          {children}
          <footer className="mt-10 text-center font-ui text-xs text-secondary">
            Gratis y de código abierto (AGPL-3.0). Sin niveles de pago, sin anuncios y sin notificaciones comerciales.
          </footer>
        </main>
        <BottomNav />
      </body>
    </html>
  );
}
