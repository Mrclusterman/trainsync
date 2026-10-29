# Revisión del frontend de las apps — 2026-10-29

Gabriel: «carece de identidad visual y orden». Auditoría con capturas de Android (emulador, datos sintéticos) y de iOS
(simulador del CI), contrastada con `docs/DISENO_ATHLETIC_PRECISION.md` (sistema de diseño del 2026-10-19, pensado para
la web) y `docs/VISION.md` §5 (estructura Hoy · Calendario · Progreso · Perfil).

## 1. Diagnóstico

### Identidad

| Problema | Dónde | Regla incumplida |
|---|---|---|
| Sin tipografía propia: fuente del sistema y una monoespaciada genérica | Las dos apps (`Theme.kt`: `FontFamily.Monospace`) | DISENO: DM Mono en números, Geist en interfaz, Instrument Sans en textos |
| Iconos de navegación con caracteres sueltos (◉ ▦ ↗ ●) en Android; en iOS, símbolos genéricos (sol, persona) | `MainActivity.kt`, `TrainSyncApp.swift` | "Nada de emoji ni caracteres como iconos"; iconos con identidad |
| Deportes con un punto de color, no con un icono | Hoy, Calendario, Progreso | Iconos de deporte propios (silueta corriendo, bici en dos trazos, ola…) |
| La fuerza es morada (#B388FF) | Calendario, Hoy | "El morado nunca como acento" |
| Las dos apps no se parecen: Android usa Material 3 por defecto y iOS los controles del sistema, con tamaños y espaciados distintos | Todas | Una sola identidad |
| ✓ como carácter para "hecha" | Calendario, Hoy | Iconos, no caracteres |

### Orden

| Problema | Dónde |
|---|---|
| Hoy no tiene cabecera: la fecha y el objetivo flotan sueltos en gris; la frase principal compite con las tarjetas | Hoy |
| Las tres preguntas ocupan media pantalla antes de la sesión | Hoy |
| Las acciones de cada sesión ("Marcar como hecha", "¿Prefieres otro deporte?", "Más detalles", registro de fuerza) se apilan sin jerarquía | Hoy |
| Los títulos no son coherentes: Perfil y Progreso tienen título; Hoy y Calendario no | Todas |
| Calendario: media pantalla vacía bajo el mes; el día seleccionado no se ve hasta tocarlo; la leyenda es una frase | Calendario |
| Perfil es un formulario largo que mezcla objetivo, plan, conexiones, recordatorio, datos y avisos legales | Perfil |
| Chips de opciones con anchos irregulares (rejilla de dos columnas con huecos) | Perfil, bienvenida |
| Sin estados vacíos ni de carga diseñados (spinners del sistema o nada) | Todas |
| Progreso: métricas en fila única muy larga, sin agrupar (cuerpo, sueño, entrenamiento) | Progreso |

### Lo que ya está bien y se conserva

Modo oscuro primario, paleta de superficies y de zonas (Z3 naranja), números en monoespaciada, módulos con radio 20,
lenguaje llano, motivos con etiqueta de evidencia.

## 2. Plan propuesto

1. **Sistema de diseño compartido** (tokens): colores, tipografía, espaciado (escala de 4), radios, elevación. Una sola
   fuente de verdad para Compose y SwiftUI.
2. **Tipografía propia empaquetada** en las dos apps (licencia SIL OFL, sin peticiones a terceros).
3. **Iconos**: conjunto de navegación y de interfaz con trazo fino (Lucide, licencia ISC) e iconos de deporte propios en
   vectores; nada de caracteres.
4. **Componentes comunes** en las dos apps, con el mismo nombre y aspecto: cabecera de pantalla, módulo, tarjeta de
   sesión, pastilla de métrica, barra de zonas, fila de lista, selector de opciones, botón principal y secundario,
   estado vacío, esqueleto de carga, hoja inferior.
5. **Reordenar cada pantalla** (orden de lectura):
   - **Hoy**: cabecera (fecha, objetivo, estado de hoy) → frase y sesión principal → acciones en una fila → preguntas
     plegadas en una tarjeta compacta → semana (7 días en fila) → avisos.
   - **Calendario**: mes compacto con iconos de deporte → el día seleccionado debajo (hoy por defecto) → leyenda con iconos.
   - **Progreso**: estado y tiempos previstos arriba → métricas agrupadas (entrenamiento, recuperación, cuerpo) →
     últimos entrenamientos.
   - **Perfil**: secciones con filas que abren su pantalla (Objetivo y carreras · Tu semana · Conexiones · Recordatorio
     · Tus datos · Aviso de salud), en vez de un formulario único.
   - **Bienvenida**: barra de progreso arriba, un paso por pantalla, tipografía grande.
6. **Movimiento con función**: cuenta de números al cargar, transición al abrir un día o una sesión, pulsación con
   escala; todo por debajo de 300 ms y respetando "reducir movimiento" del sistema.
7. **Accesibilidad**: contraste AA, tamaños de texto dinámicos, etiquetas para lectores de pantalla en iconos y
   gráficas, objetivos táctiles de 44 pt / 48 dp.
8. **Comprobación**: capturas de cada pantalla en Android (emulador) e iOS (CI) antes y después, y la lista de
   comprobación de DISENO.

## 3. Decisiones de Gabriel (2026-10-29)

| Pregunta | Decisión |
|---|---|
| Identidad | **Una nueva** (Athletic Precision queda como antecedente). Tres direcciones propuestas en el lienzo "TrainSync · direcciones visuales": A · Señal (instrumento monocromo con señal amarilla), B · Pulso (compañero de salud, tonos de bosque y piel, formas suaves), C · Cuaderno (diario editorial, cifras grandes, filetes y un rojo). Pendiente de elegir |
| Plataformas | **Misma identidad, controles nativos**: colores, fuentes, iconos y componentes comunes; navegación, menús y gestos de cada sistema |
| Modo claro | **Oscuro y claro desde ya**, según el ajuste del móvil |
| Tipografía | **Propia, empaquetada** en la app (licencia OFL, sin peticiones a internet); la familia concreta depende de la dirección elegida |
