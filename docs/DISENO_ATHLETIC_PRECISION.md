<!-- Pegado desde Claude Docs el 2026-10-19; las tablas llegaron aplanadas (una celda por línea). -->

# TrainSync — Sistema de Diseño Frontend
19 oct 2026 · @Gabriel Miralles Alvarez
Contexto y decisión de estilo
TrainSync es una app para atletas amateurs serios: corredores, ciclistas, triatletas y duatletas que leen su HRV, saben qué es el TSB y entrenan con objetivo. El frontend web es la fase de desarrollo actual, con la app móvil (React Native + Expo) como destino final, por lo que el diseño es mobile-first desde el principio.
iOS 26 / Liquid Glass — por qué no se usa directamente
Apple presentó Liquid Glass en WWDC 2025: un material traslúcido que refleja y refracta su entorno, descrito como el cambio visual más significativo desde iOS 7. Es el lenguaje de diseño nativo de iOS 26 e iPadOS 26, respaldado por un motor de renderizado propio con aceleración de hardware.
Implementarlo puramente en web tiene tres problemas concretos para TrainSync:
1. Rendimiento móvil. Los efectos de refracción requieren SVG filters complejos o shaders. En un dashboard con métricas actualizándose, esto genera drops de frames en gamas medias — exactamente el hardware que usan la mayoría de atletas.
2. Legibilidad de datos. TrainSync muestra números críticos: bpm, TSS, ritmos, series, zonas. Los fondos translúcidos que cambian según el wallpaper degradan la legibilidad. Un atleta mirando su FC objetivo en mitad de un intervalo no puede tener ambigüedad visual.
3. Identidad propia. Copiar Apple hace que la app parezca un template de iOS, no un producto con personalidad reconocible.
La solución adoptada
Tomar los principios de iOS 26 que funcionan en web — jerarquía de contenido, profundidad, fluidez, controles que se adaptan al scroll — y construir sobre ellos una identidad visual propia. El glassmorphism se usa de forma controlada y exclusivamente en navegación y modales, no en tarjetas de datos.
Identidad visual — Athletic Precision
El concepto es control total sobre el entrenamiento. La referencia visual no es una app de fitness genérica con gradientes de neón, sino la interfaz de un ciclocomputador de alta gama o la pantalla de telemetría de un corredor profesional: funcional y bello al mismo tiempo. El modo oscuro es primario.
Paleta de color
Token
Valor
Uso
--color-bg-base
#0A0A0F
Fondo base (negro con tinte azul frío)
--color-surface-1
#12121A
Tarjetas, módulos
--color-surface-2
#1A1A26
Elevación media
--color-surface-3
#22223A
Bordes, separadores
--color-glass-bg
rgba(18,18,26,0.75)
Glassmorphism (solo nav y modales)
--color-accent
#3D7EFF
Azul eléctrico — datos, acciones, nav
--color-bio
#00E5A0
Verde menta — HRV, sueño, forma óptima
--color-alert
#FF8C42
Naranja — zona gris, fatiga, advertencias
--color-danger
#FF4D6D
Rojo — fatiga extrema, zona prohibida
--color-text-primary
#F0F0FF
Blanco con tinte frío
--color-text-secondary
#8888AA
Gris azulado
Zonas de entrenamiento (color constante en toda la app):
Zona
Color
Nombre
Z1
#4ECDC4
Recuperación
Z2
#45B7D1
Aeróbico
Z3
#FFA500
Tempo — naranja de advertencia
Z4
#FF6B35
Umbral
Z5
#FF4757
VO2max
Z6
#C0392B
Anaeróbico
El naranja en Z3 no es casual: es el color universal de precaución. El atleta aprende inconscientemente que ver naranja en su distribución significa que fue a zona gris.
Tipografía
Rol
Fuente
Uso
Display / métricas
DM Mono
CTL, ATL, TSB, FC, ritmos — cualquier número protagonista
Interfaz
Geist
Etiquetas, botones, navegación
Body
Instrument Sans
Descripciones de sesiones, instrucciones
Las fuentes monoespaciadas en los números dan sensación de dato técnico, de terminal, de precisión. Un TSB: -5.6 en DM Mono parece telemetría real, no una app de bienestar.
Principios de layout
• Grid: 4 columnas en móvil, 8 en tablet, 12 en desktop
• Márgenes laterales: 16 px (móvil), 24 px (tablet)
• Radio de bordes: 8 px chips · 14 px tarjetas · 20 px módulos · 28 px modales · 999 px botones pill
• Sombras siempre con glow del color del acento, nunca sombra plana gris
Componentes clave
Módulo de métricas (CTL, ATL, TSB)
Cada métrica tiene su tarjeta con el valor en DM Mono a 48 px. El color del número cambia según estado:
Estado TSB
Color
> +10 (fresco)
--color-bio verde
0 a +10
--color-accent azul
−10 a 0
--color-text-primary neutro
< −10 (fatigado)
--color-alert naranja
< −25 (sobreentrenado)
--color-danger rojo
El módulo HRV usa siempre --color-bio y tiene un punto pulsante animado cuando el estado es elevated. El módulo de sueño muestra el score 0–100 y la duración.
Tarjeta de sesión del día
Es el elemento más visible del dashboard. Estructura:
1. Cabecera: icono SVG custom del deporte + nombre del tipo de sesión en Geist 20 px bold
2. Pills de métricas: duración · zona objetivo · TSS estimado — fondo rgba(acento, 0.12), borde rgba(acento, 0.25), fuente DM Mono 12 px
3. Bloques de estructura (calentamiento / parte principal / vuelta a la calma): tabla compacta con duración y zona de cada bloque
4. CTAs: [Empezar sesión] + [Ver detalles]
5. Alternativas: tarjetas colapsadas por deporte activo, expandibles
Barras de distribución de zonas
Para mostrar en qué zonas pasó tiempo el atleta en una actividad. Cada zona tiene su barra de 6 px de alto, border-radius: 3px, y un glow sutil del color de su zona. Si Z3 supera el 15 % del tiempo, la barra naranja lleva un icono ⚠ y una animación de shake al cargar (solo una vez).
Navegación inferior (iOS 26 inspired)
La barra inferior tiene efecto glass (backdrop-filter: blur(20px)) y se comporta de forma dinámica:
• Scroll hacia abajo: la barra se contrae — solo iconos sin etiquetas
• Scroll hacia arriba: la barra se expande — iconos + etiquetas
• Transición con Spring physics: stiffness 400, damping 30, no ease-in-out
• 4 tabs: Hoy · Plan · Objetivos · Perfil
Animaciones y microinteracciones
Principio: las animaciones en una app de datos deben ser funcionales — cada animación comunica algo, no decora.
Elemento
Animación
Duración
Carga de métricas numéricas
Count-up de 0 al valor real con easing ease-out
800 ms
Transición entre pantallas
Slide horizontal (pantalla nueva entra desde la derecha)
250 ms
HRV elevated
Punto verde con pulse — box-shadow crece y desaparece en loop
2 s
Barra Z3 > 15 %
Shake animation al cargar, solo una vez
300 ms
Tap en tarjeta de sesión
scale(1.02) durante 100 ms antes de navegar
100 ms
Skeleton loading
Shimmer de izquierda a derecha en superficie ligeramente más clara
1,5 s loop
Barra de navegación
Spring physics al expandir / contraer
Stiffness 400, damping 30
Skeleton screens en todos los estados de carga, nunca spinners. Los spinners comunican «espera»; los skeletons comunican «cargando datos».
Estados del atleta y diseño visual
El diseño cambia sutilmente según el estado del atleta. La interfaz no solo muestra datos — refleja el momento del atleta.
Estado
Condición
Efecto visual
Forma óptima
TSB > 10 + HRV elevated + sueño > 75
Glow verde sutil en el borde superior del header · Sesión de alta intensidad · «Condiciones óptimas para calidad»
Normal
TSB 0/−10 + HRV balanced + sueño 50–75
Diseño neutro, sin glow especial
Fatigado
TSB < −15 + HRV poor O sueño < 50
Tinte naranja muy sutil en borde del header (20 % opacidad) · Sesión de recuperación · «Hoy es día de recuperación. Es parte del plan.»
El estado de fatiga no se presenta como algo negativo, sino como algo controlado y previsto dentro del ciclo de entrenamiento.
Pantallas específicas
Dashboard (pantalla principal)
Scroll vertical con estas secciones en orden:
1. Header sticky glass — fecha + dot de color según estado + icono sync intervals.icu
2. Grid 2×2 de métricas — TSB · CTL · HRV · Sueño, cada uno con valor grande en DM Mono y estado en color
3. Tarjeta de sesión del día — protagonista visual, ~70 % del ancho
4. Próximos 3 días — scroll horizontal de tarjetas compactas
5. Última actividad — tarjeta con actividad importada de intervals.icu + análisis de zona (¿zona gris?)
Sesión detallada
Estructura de scroll:
1. Hero con deporte + tipo de sesión, fondo con noise texture sutil del color del deporte
2. Pills de métricas: duración · zonas · TSS estimado
3. Objetivo fisiológico: texto en Instrument Sans
4. Estructura bloque a bloque: tarjetas expandibles (calentamiento, principal, vuelta a la calma)
5. Notas de adaptación: «Si te sientes mal…» / «Si te sientes bien…» con borde izquierdo de color acento o alert
6. Alternativas: acordeón colapsado al final
Plan de 14 días
Calendario horizontal con scroll. Día actual destacado con borde accent. Días pasados al 50 % de opacidad. Cada día muestra icono de deporte + tipo de sesión + duración. Al hacer tap: bottom sheet iOS-style con la sesión completa.
Debajo del calendario: barra de TSS planificado vs real para la semana actual.
Onboarding
Barra de progreso lineal arriba, no puntos. Cada paso ocupa toda la pantalla. Tipografía grande, explicaciones cortas, acción clara.
• Paso 1 — Deportes: tarjetas grandes con icono SVG custom. Al seleccionar: scale(1.05) + check con animación de stroke SVG dibujándose
• Paso 2 — Conexión intervals.icu: campo API key + botón «Verificar conexión» con loader → «✓ Conectado · iXXXXXX · N actividades importadas»
• Paso 3 — Datos fisiológicos: datos detectados en verde (confirmados), los que faltan en naranja con test sugerido
Implementación técnica
Next.js (web)
CSS variables en :root para todo el sistema de diseño. Tailwind para utilidades, CSS modules para componentes principales. No mezclar inline styles con CSS modules.
Fuentes vía next/font/google: DM_Mono, Geist, Instrument_Sans.
Glassmorphism (solo nav y modales):
.glass-surface {
  background: rgba(18, 18, 26, 0.75);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-lg);
}
Glow de acento (métricas importantes):
.metric-glow {
  box-shadow: 0 0 24px var(--color-accent-glow),
              0 0 0 1px rgba(61, 126, 255, 0.1);
}
Skeleton shimmer:
@keyframes shimmer {
  0%   { background-position: -200% 0; }
  100% { background-position:  200% 0; }
}
.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-surface-1) 25%,
    var(--color-surface-2) 50%,
    var(--color-surface-1) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}
Count-up en números: react-countup o requestAnimationFrame, duración 800 ms, easing ease-out.
React Native / Expo (móvil)
• StyleSheet.create() con las variables del sistema de diseño
• expo-blur para efectos glass en navegación
• react-native-reanimated para Spring animations (stiffness: 400, damping: 30, mass: 1)
• react-native-svg para iconos de deporte custom
• Tap feedback en tarjetas: scale(0.97) → spring back a 1.0
Iconografía
Lucide Icons (stroke-width="1.5", no 2) para iconos generales. Iconos de deporte como SVG custom: silueta estilizada para running, bicicleta en dos trazos para ciclismo, ola para natación, combinación para triatlón y duatlón. No usar emoji como iconos de interfaz.
Lo que nunca debe aparecer y checklist
Prohibido en TrainSync
• Gradientes de arcoíris o múltiples colores en un mismo elemento
• Ilustraciones de personajes corriendo o figuras de stock
• Sombras de colores muy saturados tipo «neón de gaming»
• Botones con gradientes de verde a azul (cliché de fitness app)
• El color morado como acento principal — el más sobreutilizado en apps de salud
• Animaciones de celebración excesivas (confetti) al completar una sesión. TrainSync celebra con un mensaje limpio y los datos del esfuerzo.
• Mezcla de estilos: si una pantalla es flat, todas lo son; si usa glassmorphism, solo en los elementos definidos
• Glassmorphism en tarjetas de datos — solo en nav y modales
• Emoji como iconos de interfaz
Checklist de coherencia visual
Antes de implementar cualquier pantalla:
[ ] Todos los números de métricas usan DM Mono
[ ] Los colores de zona son constantes en toda la app (Z3 siempre naranja)
[ ] El fondo base es siempre #0A0A0F
[ ] El glassmorphism solo está en navegación y modales
[ ] Las métricas cambian de color según su estado (TSB negativo → naranja)
[ ] Los iconos de deporte son siempre los SVG custom, no emoji
[ ] Hay skeleton screens en todos los estados de carga
[ ] La barra inferior se comporta igual en todas las pantallas
[ ] Ningún elemento usa morado como acento
[ ] Las animaciones tienen duración < 300 ms (excepto count-up de números)
[ ] El modo oscuro es primario y no hay fondos blancos en modo dark
