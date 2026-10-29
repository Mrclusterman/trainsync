# Sistema de diseño Pulso — apps nativas

> **Estado:** en uso desde el 2026-10-29 · Sustituye a `DISENO_ATHLETIC_PRECISION.md` en las apps (la web prototipo
> sigue con el anterior). Elegido por Gabriel entre diez direcciones (`REVISION_FRONTEND_2026-10-29.md` §3).

Un compañero de salud y rendimiento: tonos de bosque y piel, formas suaves y frases cálidas. **El color dice cómo
estás** (verde bien, ámbar con cuidado, gris descanso) y sale de la misma decisión del motor que la sesión.
Misma identidad en Android e iOS; navegación, hojas, menús y campos de texto, los de cada sistema.

## Tokens

Fuente de verdad: `androidApp/.../Theme.kt` y `iosApp/iosApp/Components.swift` (mismos valores; si cambias uno, cambia
el otro). Modo claro y oscuro según el ajuste del móvil.

| Token | Oscuro | Claro | Uso |
|---|---|---|---|
| bg | `#0F1412` | `#F3F0E9` | Fondo de pantalla |
| surface | `#18201C` | `#FFFFFF` | Tarjetas |
| chip | `#243029` | `#ECE7DC` | Pastillas, botón secundario, pistas de barras, separadores |
| text | `#EEF2EE` | `#1B2420` | Texto |
| muted | `#A3B0A8` | `#5D6762` | Texto secundario |
| ok | `#5FD3A4` | `#1F7A5C` | Acción principal, estado bien, hecho |
| onOk | `#0B1F16` | `#FFFFFF` | Texto sobre ok |
| okSoft | `#16302A` | `#DCEFE5` | Fondo de la tarjeta de estado "bien", opción elegida |
| band | `#1E4A3B` | `#CDE8DA` | Banda de "tu rango" |
| caution | `#F2B95A` | `#9A6400` | Ajuste, fuera de rango hacia el lado malo |
| cautionSoft | `#3A2E17` | `#F6E7C8` | Fondo de la tarjeta de estado "con cuidado" y del aviso de salud |
| danger | `#FF8A73` | `#B3402A` | Borrar datos |
| Zonas Z1–Z6 | `#4ECDC4` `#45B7D1` `#FFA500` `#FF6B35` `#FF4757` `#C0392B` | iguales | Siempre las mismas (app, reloj, documentación); texto `#0B1F22` encima |

Sin morado. Radios: tarjeta 24, tarjeta de estado 20, pastilla de deporte 12, botones en cápsula. Relleno de tarjeta 18;
separación entre tarjetas 14; márgenes laterales 18.

## Tipografía

Empaquetada (sin peticiones a internet), licencia OFL: **Figtree** (400–800) para la interfaz e **IBM Plex Mono**
(400–600) para números (duraciones, tiempos, valores). Títulos de pantalla 26 ExtraBold; título de sesión 24
ExtraBold; secciones 15 Bold; texto 14–15. En iOS crecen con el tamaño de texto del sistema (`relativeTo`).

## Iconos

Trazo 1,8 sobre rejilla de 24, puntas redondas. Navegación e interfaz: Lucide (ISC). Deportes: propios (carrera,
caminar, fuerza) y Lucide (bici, olas). Mismo dibujo en Android (`res/drawable/ic_*.xml`) e iOS
(`Assets.xcassets`, SVG como plantilla). Nada de caracteres sueltos (✓, ◉, ‹) como iconos.

## Componentes (mismo nombre en las dos apps)

ScreenTitle · Module (tarjeta) · StateCard (cómo estás: icono, color y motivos) · SessionCard (pastilla de deporte,
duración, título llano, pasos con ZoneChip) · PrimaryButton/PrimaryStyle y SecondaryButton/SecondaryStyle ·
WeekDots (semana en círculos) · RangeBar (valor frente a tu rango) · ProgressLine · SportBadge · ReasonRow.

## Pantallas

- **Hoy:** título y fecha → objetivo → tarjeta de estado → sesiones (acciones: "La he hecho", "Cambiar", enviar al
  reloj) → semana (minutos OMS y círculos).
- **Progreso:** Tu cuerpo (barras de rango) → Forma (estado y curva) → Actividad semanal (OMS) → tiempos previstos →
  últimos entrenamientos.
- **Calendario:** el mes y, debajo, el día elegido (hoy al abrir) con sus sesiones y acciones, sin hoja aparte: tocar
  días seguidos repasa la semana sin abrir y cerrar nada.
- **Días de descanso** (Hoy y Calendario, en los días que quedan de la semana): "Prefiero entrenar hoy" recalcula la
  semana con ese día disponible.
- **Perfil y bienvenida:** con los tokens y componentes; su reordenación (Perfil en secciones, un paso por pantalla)
  sigue pendiente del plan.
