import { defineConfig, type Plugin } from "vite"
import react from "@vitejs/plugin-react"

// Solo scripts, estilos y fuentes propios, y conexiones solo a intervals.icu: un script ajeno no corre y, si corriera,
// no podría mandar la clave ni los datos de salud a otro sitio. Solo en el build: el servidor de desarrollo usa estilos en línea
const CSP = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self'",
  "font-src 'self'",
  "img-src 'self' data:",  // los iconos pequeños van incrustados como data:
  "connect-src https://intervals.icu",
  "base-uri 'none'",
  "form-action 'none'",
].join("; ")

const csp = (): Plugin => ({
  name: "csp",
  apply: "build",
  transformIndexHtml: (html) =>
    html.replace('<meta charset="utf-8" />', `<meta charset="utf-8" />\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
})

// Fuentes e iconos de Pulso salen de iosApp/ (una sola copia en el repo); base relativa para alojarla en cualquier ruta
export default defineConfig({ plugins: [react(), csp()], base: "./", server: { fs: { allow: [".."] } } })
