# CUMPLIMIENTO — TrainSync

> **Versión:** 0.2 · **Fecha:** 2026-10-20
> **Ámbito:** app gratuita y open source, responsable en España, usuarios en la UE, que trata datos de salud (FC, HRV, sueño) importados de intervals.icu.
> **No es asesoramiento legal.** Las referencias legales se han contrastado con resúmenes y fuentes secundarias, no siempre con el texto del BOE o del DOUE; antes de publicar la app conviene revisarlas con un profesional.

Etiquetas: **[Verificado]** comprobado en el código o en la app real · **[Fuente]** respaldado por la fuente citada (secundaria salvo que se diga) · **[No verificado]** pendiente de confirmar.

---

## 0. Lo más importante

1. **Los datos de TrainSync son datos de salud.** FC, HRV, sueño y métricas continuas de wearables se tratan como categoría especial del art. 9 RGPD. Hace falta **consentimiento explícito**, específico para datos de salud y separado de la aceptación de los términos (art. 9.2.a). [Fuente: guías de cumplimiento sobre wearables; los reguladores tienden a no aceptar el argumento "es un dispositivo de consumo"]
2. **Nada de esto bloquea el uso personal actual** (un solo usuario, en local, con su propia cuenta). **Sí bloquea abrir la app a otras personas**: todo lo marcado "antes de publicar" debe estar hecho antes de la Fase 1 en producción.

---

## 1. Estado por punto

| # | Requisito | ¿Aplica? | Estado hoy | Cuándo | Base / nota |
|---|---|---|---|---|---|
| 1 | Política de privacidad | Sí | Pendiente | Antes de publicar | RGPD arts. 13-14. Debe cubrir datos de salud (art. 9), intervals.icu como origen, plazos de conservación y derechos |
| 2 | Términos de uso | Sí | Pendiente | Antes de publicar | Incluir que TrainSync **no es consejo médico** ni producto sanitario y que el usuario entrena bajo su responsabilidad |
| 3 | Política de reembolso | **No**, mientras sea gratis | — | Si hay pago o donaciones con contraprestación | — |
| 4 | Política de cookies | Hoy no hay cookies | [Verificado: 0 cookies en / y /plan] | Fase 1 (cookie de sesión) | Las cookies técnicas estrictamente necesarias (sesión, autenticación) están exentas de consentimiento (art. 22.2 LSSI, guía AEPD) [Fuente: resúmenes de la guía AEPD]. Se describirán en la política de privacidad |
| 5 | Banner de consentimiento de cookies | **No**, mientras solo haya cookies técnicas | — | Solo si se añade analítica o terceros | Si se añade, debe tener "rechazar" con el mismo peso que "aceptar" (ver #9) |
| 6 | Consentimientos en formularios | Sí | Pendiente (no hay formularios) | Fase 1 (registro) | Casillas **sin marcar**; consentimiento de datos de salud **separado** de términos y privacidad; registrar fecha y versión aceptada |
| 7 | Sin datos innecesarios | Sí | Parcial | Fase 1 | Hoy no se guarda nada; `/athlete` de intervals.icu devuelve email y otros datos que el código no usa ni guarda [Verificado]. **Conflicto:** el modelo de la Memoria guarda `raw_data` JSONB completo de actividades y wellness "para auditoría": revisar en Fase 1 y guardar solo los campos usados |
| 8 | Auditar SDKs de terceros | Sí | Hecho para el frontend | Revisar en cada dependencia nueva | [Verificado: el navegador solo contacta con el propio dominio]. Las fuentes se sirven desde el propio servidor (`next/font`), lo que evita el problema de la sentencia de Múnich de 2022 sobre Google Fonts [Fuente]. Sin analítica ni trackers. Backend: solo intervals.icu (y Ollama local, opcional) |
| 9 | Eliminar dark patterns | Sí (principio) | Sin incidencias | Siempre | Sin casillas premarcadas, sin urgencias falsas, borrar la cuenta igual de fácil que crearla |
| 10 | Eliminar costes ocultos | **No** (gratis) | — | Si hay pagos | — |
| 11 | Eliminar reseñas falsas | No hay reseñas | — | Si se muestran testimonios | Directiva Ómnibus: prohibido publicar reseñas sin verificar que son de usuarios reales; hay que indicar cómo se verifican [Fuente] |
| 12 | Eliminar afirmaciones sin respaldo | **Sí** | Parcial | Antes de publicar | El motor etiqueta cada regla ([Verificado]/[Práctica]/[Inferencia]). Revisar textos públicos: la Memoria dice "científicamente fundamentados" y parte de las reglas son práctica o inferencia. Usar "basado en literatura citada" |
| 13 | Texto alternativo | Sí | Cumple | Siempre | No hay imágenes; los iconos SVG son decorativos (`aria-hidden`) junto a texto; el icono de zona gris tiene `aria-label` [Verificado]. Toda imagen futura necesita `alt` |
| 14 | Contraste de color | Sí | **Corregido** | Siempre | Auditado con la fórmula WCAG: 2 fallos corregidos (chip Z6 3,63 → 4,82; botón sobre acento 3,31 → 5,29). El resto ≥ 4,5:1 [Verificado] |
| 15 | Navegación por teclado | Sí | Cumple en principio | Revisión manual pendiente | Elementos nativos (`button`, `a`, `details`, `dialog` con Esc), foco visible [Verificado en código]. Falta una prueba manual completa solo con teclado |
| 16 | Datos del responsable | Sí | Pendiente | Antes de publicar | RGPD art. 13: identidad y contacto del responsable siempre. LSSI art. 10 (NIF, contacto) si hay actividad económica; si una web gratuita sin actividad económica queda fuera de la LSSI está [No verificado] |
| 17 | Consentimiento de edad (menores) | Sí | Pendiente | Fase 1 (registro) | España: 14 años (LOPDGDD art. 7) [Fuente: AEPD]. Menores de 14 necesitan consentimiento de padres o tutores. **Propuesta:** no admitir menores de 14 en v1 (declaración de edad en el registro) |
| 18 | Enlace de baja en emails | Solo si se envían emails | No hay emails | Si se añaden avisos por email | Emails no transaccionales: dirección o enlace de baja en cada envío (LSSI art. 21) [Fuente: resoluciones AEPD] |
| 19 | Licencias de fuentes e imágenes | Sí | **Cumple** | — | DM Mono, Geist e Instrument Sans: SIL OFL 1.1 [Fuente: repositorios y Google Fonts, secundario]. Iconos propios. Proyecto bajo **AGPL-3.0-only** (`LICENSE`, texto oficial de gnu.org); avisos en `THIRD_PARTY_NOTICES.md`. **Al publicar:** la AGPL (sección 13) obliga a ofrecer el código fuente a los usuarios del servicio en red → enlace al repositorio visible en la app |
| 20 | Solicitud de borrado de datos | Sí | Pendiente | Fase 1 | RF-01 ya lo exige. RGPD art. 17; responder en ≤ 1 mes, ampliable 2 meses avisando (art. 12.3) [Fuente: decisiones publicadas por el EDPB]. Implementar como acción en el perfil: borra todos los datos y la API key cifrada |

---

## 2. Otros puntos que salen de la revisión

| Punto | Nota |
|---|---|
| Accesibilidad legal (European Accessibility Act) | Desde el 28-06-2025, estándar EN 301 549 (WCAG 2.1 AA). Las microempresas que prestan servicios estarían exentas [Fuente, secundaria; alcance exacto no verificado]. **Decisión:** usar WCAG 2.1 AA como objetivo de todos modos |
| Portabilidad (art. 20 RGPD) | Exportar los datos del usuario en formato legible por máquina, junto al borrado |
| Seguridad (art. 32 RGPD) | Ya previsto en la Memoria: API keys cifradas (AES-256), contraseñas con bcrypt, TLS |
| Producto sanitario | Un planificador de entrenamiento sin finalidad médica en principio no es producto sanitario [No verificado]. Evitar afirmaciones de diagnóstico o tratamiento |
| Proveedor de datos (intervals.icu) | Revisar los términos de su API para uso multiusuario antes de publicar [No verificado] |
| Historial de git con datos personales | La Memoria guardaba el ID de atleta y datos fisiológicos del desarrollador. Se anonimizaron y se reescribió todo el historial (2026-10-20) [Verificado: ningún commit los contiene]. Pero tras reescribir y hacer force push, los commits antiguos siguen accesibles en GitHub "directamente por su SHA-1 en vistas en caché" [Fuente: GitHub Docs]. No hay forks ni PR que los referencien [Verificado: 0 y 0]. **Hecho (2026-10-20):** repo borrado en GitHub y recreado con el historial limpio [Inferencia: GitHub Docs solo propone contactar con Soporte, que "no eliminará datos no sensibles"]. El commit antiguo `55fae10` ya responde "No commit found" [Verificado] |

---

## 3. Fuentes

- RGPD art. 9 y wearables: [GDPR Local](https://gdprlocal.com/gdpr-for-wearable-technology/), [Thryve](https://www.thryve.health/blog/wearable-data-special-category-gdpr), [Momentum](https://www.themomentum.ai/blog/gdpr-consent-requirements-health-data)
- Edad de consentimiento: [AEPD, infografía](https://www.aepd.es/infografias/infografia-consentimiento-menores.pdf), [INEAF](https://www.ineaf.es/tribuna/rgpd-tratamiento-de-datos-de-menores/)
- LSSI art. 10: [Papelea](https://papelea.com/es/leyes/ley-de-servicios-de-la-sociedad-de-la-informacion-y-comercio-electronico-lssi-ce/titulo-ii_capitulo-ii_seccion-1-obligaciones), [Infoautónomos](https://www.infoautonomos.com/tecnologia-pymes-autonomos/la-ley-de-servicios-de-la-sociedad-de-la-informacion-lssi)
- Cookies: [ECIJA sobre la guía AEPD](https://www.ecija.com/actualidad-insights/guia-aepd-uso-cookies/)
- LSSI art. 21: [AEPD E/02319/2017](https://www.aepd.es/documento/e-02319-2017.pdf)
- Borrado y plazos: [EDPB, decisión BE 2019-05](https://www.edpb.europa.eu/system/files/2021-10/be_2019-05_right_to_erasure_decisionpublic.pdf)
- Google Fonts y RGPD: [The Hacker News](https://thehackernews.com/2022/01/german-court-rules-websites-embedding.html), [Captain Compliance](https://captaincompliance.com/education/google-fonts-and-gdpr-what-the-munich-ruling-requires-and-how-to-self-host-your-way-out-of-exposure/)
- Reseñas (Ómnibus): [Make Influence](https://www.makeinfluence.com/en/academy/fake-and-manipulated-reviews-what-the-eu-omnibus-directive-bans), [Mondaq](https://mondaq.com/dodd-frank-consumer-protection-act/1189542/the-omnibus-directive-consumer-reviews)
- European Accessibility Act: [e-include](https://e-include.eu/web-accessibility/european-accessibility-act/), [Herzog](https://herzoglaw.co.il/en/news-and-insights/the-european-accessibility-act-key-considerations-for-e-commerce-providers/)
- Datos sensibles en el historial de git: [GitHub Docs, Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- Licencias de fuentes: [DM Mono](https://gitee.com/doneself/dm-mono), [Geist](https://fr.github.com/vercel/geist-font), [Instrument Sans](https://fonts.google.com/specimen/Instrument%2BSans/about)
