---
name: fepzon-ui
description: Sistema de diseño "Moderno cálido" de FEPZON (Spring Boot + Thymeleaf). Úsala SIEMPRE antes de crear o modificar cualquier pantalla, plantilla HTML, CSS o JS que pinte interfaz en src/main/resources (admin, mesero, cocina, catálogo, carta QR, cuenta, superadmin, login). Cubre tokens, tipografía, íconos SVG, componentes y las reglas que verifican los tests.
---

# FEPZON UI — "Moderno cálido"

Interfaz para restaurantes y comercios: cálida, legible, táctil y rápida. Cada negocio
define 3 colores de marca (primario, secundario, acento); **todo lo demás** (superficies,
tipografía, formas, sombras, íconos) es del sistema y no cambia por negocio.

## Reglas duras (las verifican los tests — no romper)

1. **Cero colores literales fuera de `static/css/theme.css`.** `TemaVisualTests.viewsDoNotHardcodeColors`
   falla con cualquier `#hex`, `rgb(`, `rgba(`, `hsl(` en propiedades de color/fondo/borde/sombra
   de HTML, JS o CSS. Si necesitas un color nuevo, crea un token en `theme.css`.
2. **Solo variables definidas.** Toda `var(--x)` debe existir en `theme.css` o la genera
   `TemaNegocio.variables(...)` (`--brand-{primary|secondary|accent}[-hover|-on|-soft|-border|-text|...]`).
3. **Contraste WCAG AA** en claro y oscuro: `--text-main`/`--text-muted` ≥ 4.5 sobre page/card/raised,
   `--border-strong` ≥ 3 sobre card, cada `--status-*-text` ≥ 4.5 sobre su `-soft` y las superficies.
   El primer bloque `:root {` y `:root[data-theme="dark"] {` de `theme.css` no pueden contener `{}` anidadas.
4. Si cambias superficies en `theme.css`, actualiza las constantes `SUPERFICIE_*` de
   `service/TemaNegocio.java` (con ellas se calculan los colores de marca legibles).
5. **Sin comentarios** en CSS/JS/Java (convención del proyecto, ver `docs/DESARROLLO.md`).
6. **CSP**: sin CDNs. Fuentes en `/css/fonts/`, íconos en `/img/iconos.svg`, todo same-origin.
   Rutas públicas sin login: `/css/**`, `/js/**`, `/img/**`.

## Tokens (theme.css)

| Grupo | Tokens |
|---|---|
| Superficies | `--surface-page` (crema), `--surface-card` (blanco), `--surface-raised`, `--surface-sunken` |
| Texto | `--text-main`, `--text-muted`, `--text-subtle` (solo decorativo, no para texto esencial) |
| Bordes | `--border-main` (sutil), `--border-strong` (controles) |
| Estado | `--status-{success,warning,error,info}` + `-on`, `-soft`, `-text`, `-border` |
| Marca | `--brand-primary-*`, `--brand-secondary-*`, `--brand-accent-*`; acciones: `--action-primary[-hover|-on]`, `--action-accent[-hover|-on]` |
| Cabeceras | `--header-bg/-text/-control` (barra de color de marca, vistas operativas), `--topbar-bg` (barra translúcida del admin) |
| Forma | `--radius-xs 6` `--radius-sm 10` `--radius-md 14` `--radius-lg 20` `--radius-xl 28` `--radius-pill` |
| Espacio | `--space-1..7` = 4 8 12 16 24 32 48 |
| Sombra | `--elev-1` (reposo), `--elev-2` (hover), `--elev-3` (flotante), `--shadow-card`, `--shadow-overlay`, `--ring-brand` |
| Movimiento | `--dur-fast 150ms`, `--dur-base 220ms`, `--ease-out` |
| Tipo | `--font-ui` (Plus Jakarta Sans variable 200–800), `--fs-xs .75` `--fs-sm .875` `--fs-md 1` `--fs-lg 1.125` `--fs-xl 1.375` `--fs-2xl 1.75` `--fs-3xl 2.25` rem |

## Tipografía

- Una sola familia: **Plus Jakarta Sans** (variable, alojada en `/css/fonts/`).
- Títulos: peso 750–800, `letter-spacing: -0.02em`. Cuerpo: 400–500. Etiquetas: 600–700.
- Etiquetas de sección: `--fs-xs`, 700, mayúsculas, `letter-spacing: .08em`, `--text-muted`.
- Números (precios, KPIs, tablas): `font-variant-numeric: tabular-nums`.

## Íconos

Sprite SVG de línea (estilo Lucide, trazo 2, 24×24) en `static/img/iconos.svg`.
En Thymeleaf:

```html
<svg th:replace="~{fragments/icono :: i('home')}"></svg>
```

En JS: `Icono.crear('home')` (devuelve un `<svg>` listo; definido en `tema.js`, carga en todas las páginas).
`Admin.vacio(el, { icono: '⚠️', ... })` traduce automáticamente los emojis de sistema conocidos a SVG
(`Icono.desdeEmoji`).
Los íconos heredan `currentColor` y miden `1.25em` (clase `.ico`; `.ico-sm`, `.ico-lg`).
Los **emojis solo se usan para contenido del negocio** (íconos de categorías elegidos por el usuario),
nunca para la navegación ni los botones del sistema.
Para añadir un ícono: agrega un `<symbol id="i-nombre" viewBox="0 0 24 24">` al sprite.

## Componentes base

- **Botones**: `.button-primary` (acción principal, 1 por vista), `.button-secondary` (contorno sobre card),
  `.button-ghost` (sin fondo), `.button-danger`. Alto mínimo 44px (táctil), radio `--radius-md`, peso 700.
  Ícono + texto con `gap: var(--space-2)`.
- **Campos**: `.field` — fondo card, borde `--border-strong` 1px, foco con `--ring-brand`, alto 44–48px.
- **Tarjetas**: `.admin-card` — card, borde `--border-main`, radio `--radius-lg`, sombra `--elev-1`.
- **KPIs**: `.admin-stat` con `.admin-stat-ico` (ícono en burbuja de color suave).
- **Badges**: `.badge`, `.badge-ok|warn|info|off` — píldora, `--fs-xs`, peso 700.
- **Diálogos**: `<dialog class="dialog-ui">` y `.modal-ui`; avisos flotantes `.notice-ui`.
- **Estados vacíos**: `.admin-empty` con ícono, título, texto y acción.

## Layout

- **Admin**: sidebar fija de altura completa en ≥960px (`--admin-nav-w: 272px`) con la marca arriba;
  topbar translúcida (`--topbar-bg` + `backdrop-filter`) con el título de sección y el menú de sesión.
  En móvil la sidebar es un cajón deslizable.
- **Vistas operativas** (mesero, cocina, catálogo): barra superior con color de marca (`--header-bg`),
  objetivos táctiles grandes (≥48px), contraste alto, información densa pero escaneable.
- **Carta QR pública**: mobile-first, foto protagonista, tarjetas redondeadas, CTA de acento.

## Checklist antes de terminar un cambio visual

- [ ] Sin colores literales fuera de `theme.css`; solo variables existentes.
- [ ] Probado en claro **y** oscuro, en 375px y en ≥1280px.
- [ ] Foco visible en todo lo interactivo; objetivos ≥44px.
- [ ] `prefers-reduced-motion` respetado (ya está global en `theme.css`).
- [ ] Tests: `TemaVisualTests` y `AdminVistasTests` (Maven en Docker, ver `docs/DESARROLLO.md`).
