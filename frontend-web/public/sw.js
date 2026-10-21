// Sin conexión (replica/fixes.md #6): las páginas de la app se guardan al verlas y, sin red, se muestra la última copia.
// Solo GET: las acciones (marcar hecha, mover…) necesitan conexión. Las páginas guardadas tienen datos de salud: se
// borran al ir a /entrar (cerrar sesión, sesión caducada o cuenta borrada).
const PAGES = "trainsync-pages-v1";
const ASSETS = "trainsync-assets-v1";
const SAVED = ["/", "/plan", "/salud", "/salud/perfil"];

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => ![PAGES, ASSETS].includes(k)).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;

  if (url.pathname.startsWith("/_next/static/")) {  // con hash en el nombre: no cambian nunca
    event.respondWith(caches.open(ASSETS).then(async (cache) => {
      const hit = await cache.match(request);
      if (hit) return hit;
      const res = await fetch(request);
      if (res.ok) cache.put(request, res.clone());
      return res;
    }));
    return;
  }

  if (request.mode !== "navigate") return;
  if (url.pathname.startsWith("/entrar")) {
    event.waitUntil(caches.delete(PAGES));
    return;
  }
  if (!SAVED.includes(url.pathname)) return;
  event.respondWith(fetch(request).then((res) => {
    if (res.ok && !res.redirected) {
      const copy = res.clone();
      caches.open(PAGES).then((cache) => cache.put(url.pathname, copy));
    } else if (res.redirected && new URL(res.url).pathname.startsWith("/entrar")) {
      caches.delete(PAGES);
    }
    return res;
  }).catch(async () => (await caches.match(url.pathname, { cacheName: PAGES }))
    ?? new Response("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
      + "<body style='background:#0a0a0f;color:#e8e8ef;font-family:sans-serif;padding:24px'>"
      + "<h1>Sin conexión</h1><p>Esta página aún no se ha guardado en este dispositivo. Ábrela una vez con conexión.</p>",
      { headers: { "Content-Type": "text/html; charset=utf-8" } })));
});
