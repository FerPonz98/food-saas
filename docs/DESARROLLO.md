# Desarrollo

Referencia técnica para quien mantenga o extienda el sistema: arquitectura, modelo de datos, convenciones, API y pruebas.

## Arquitectura

Aplicación monolítica Spring Boot **multiempresa** que sirve las vistas Thymeleaf y una API JSON, con WebSocket STOMP/SockJS para las actualizaciones en tiempo real. El frontend es HTML, CSS y JavaScript sin frameworks ni proceso de build. Una sola instalación atiende a varios negocios con datos aislados; los planes y módulos se describen en [Plataforma](PLATAFORMA.md).

```text
src/main/java/com/sistema/pedidosCori/
  components/   PlataformaInicial (planes, negocio principal y superadministrador),
                DataInitializer (usuarios iniciales, categorías y menú de ejemplo del negocio principal) y
                RecuperacionAcceso (contraseña temporal emitida desde el servidor)
  config/       SecurityConfig, NegocioFiltro, ResolutorNegocio, ModuloInterceptor, RequiereModulo,
                WebSocketConfig, AuditoriaConfig, LoginThrottleFilter, CuentaPendienteFilter
  controller/   Páginas (PageController, AdminPageController, CuentaController, CartaPublicaController,
                SuperAdminController), API (ApiController, AdminApiController, CategoriaController,
                PlatoImagenController, ConfiguracionNegocioController, CartaQrController, LlamadoController,
                SolicitudPedidoController, DivisionCuentaController, PwaController), advices y manejo de errores
  models/       entity, dao (Spring Data JPA) y dto
  service/      NegocioActual, EjecutorNegocio, ModuloService, PlataformaService, pedidos (ComandaService),
                carta pública, llamados, pedidos desde la carta, división de cuenta, usuarios, cuenta,
                política de contraseñas, términos, categorías, fotos, configuración, tema, auditoría,
                resumen y reportes
src/main/resources/db/migration/   Migraciones Flyway (V1 esquema base, V2 multiempresa, V3 módulos Pro)
src/main/resources/
  templates/    login, cuenta, terminos, mesero, cocina, catalogo, admin/* y fragments/*
  static/css/   theme.css (tokens), admin.css (panel), catalogo.css y una hoja por página
  static/js/    security.js, tema.js, sesion.js, branding.js, borradores.js, cuenta.js,
                admin-shell.js, catalogo.js, un script por página del panel y vendor/
                (SockJS, STOMP y html2canvas servidos localmente, sin CDN)
src/test/       Pruebas con H2 (src/test/resources/application.properties)
```

## Base de datos y migraciones

El esquema lo crean y actualizan las **migraciones Flyway** de `src/main/resources/db/migration`; Hibernate solo lo **valida** al arrancar (`ddl-auto=validate`), así que una entidad que no coincide con la base impide iniciar.

- `V1__esquema_base.sql` recrea el esquema de un solo negocio. Es idempotente (`IF NOT EXISTS`) para completar también las bases creadas por versiones anteriores, cuando Hibernate administraba el esquema. Con `baseline-on-migrate` y `baseline-version=0`, una base existente se marca en la versión 0 y recibe V1, V2 y V3.
- `V2__multiempresa_y_planes.sql` crea planes, negocios y el registro de la plataforma, inserta el negocio 1 (`principal`, Premium) y asigna a él todos los datos existentes.
- `V3__modulos_pro.sql` agrega la confirmación de llamados, los pedidos desde la carta y los pagos parciales.

Para cambiar el esquema, crea `V4__descripcion.sql` (nunca edites una migración ya publicada) y ajusta la entidad. Las migraciones usan SQL de PostgreSQL; las pruebas usan H2 con `create-drop` y Flyway desactivado, así que **una migración nueva se verifica contra PostgreSQL** (por ejemplo, levantando `docker compose` sobre una copia de la base) antes de entregarla.

## Multiempresa

- **Aislamiento.** Las entidades de un negocio (`Pedido`, `DetallePedido`, `Plato`, `PresentacionPlato`, `PlatoImagen`, `Categoria`, `RegistroAuditoria`, `LlamadoMesa`, `SolicitudPedido`, `SolicitudPedidoItem`, `PagoParcial`, `PagoParcialItem`) tienen la columna `negocio_id` marcada con `@TenantId` de Hibernate. Hibernate la completa al guardar y la agrega como filtro a **todas** las consultas: JPQL, derivadas, `findById` y actualizaciones masivas (`AislamientoHibernateTests` lo verifica). No escribas SQL nativo sobre estas tablas.
- **Negocio actual.** `NegocioFiltro` (dentro de la cadena de seguridad) fija `NegocioActual` en cada solicitud: el negocio del usuario autenticado (`UsuarioSesion.negocioId`), el de la dirección pública `/n/{slug}/…` o `/api/publico/{slug}/…`, o la **raíz** (`-1`) para el superadministrador, que ve todos los negocios. Sin negocio (`0`), las consultas no devuelven nada. `ResolutorNegocio` entrega ese valor a Hibernate al abrir cada sesión.
- **Cambiar de negocio en código.** Usa `EjecutorNegocio.en(negocioId, () -> …)`: abre una transacción y una sesión nuevas con ese negocio (la sesión abierta por la vista ya tiene fijado el negocio de la solicitud). Las tareas de arranque, la auditoría y la plataforma lo usan.
- **No están aisladas por `@TenantId`:** `Usuario` (tiene `negocio_id` explícito porque el ingreso busca por nombre de usuario en todos los negocios; los nombres de usuario son únicos en toda la plataforma y `UsuarioService` filtra por negocio), `ConfiguracionNegocio` (su `id` es el del negocio), `Negocio`, `Plan` y `RegistroPlataforma`.
- **Suspensión.** Si el negocio está suspendido o cancelado, el ingreso se rechaza (`LockedException` → `/login?suspendido`), las páginas redirigen a `/suspendido`, la API responde `403 NEGOCIO_SUSPENDIDO` y la carta pública responde 404.
- **Tiempo real.** Los clientes se suscriben a `/topic/cocina`, `/topic/mesero`, `/topic/stock` y `/topic/menu` como siempre; `WebSocketConfig` reescribe la suscripción a `/topic/n{negocio}/…` según el usuario de la sesión y hace lo mismo con cada mensaje que publica el servidor, según `NegocioActual`. Se rechazan las suscripciones a temas de otro negocio y los mensajes que los clientes intenten publicar en `/topic`.

## Planes y módulos

- `Modulo` enumera las funciones que dependen del plan. Los módulos de un negocio son los de su plan, más o menos los ajustes de `negocio_modulo`. `ModuloService` los calcula y los guarda 15 segundos en memoria; los cambios desde la plataforma invalidan esa memoria.
- **Backend:** `@RequiereModulo(Modulo.X)` en un controlador o método. `ModuloInterceptor` responde `403 {codigo: MODULO_NO_INCLUIDO, modulo}` en la API, redirige a `/admin/plan?modulo=X` en el panel y responde 404 en las páginas públicas. Los servicios que tienen reglas parciales lo verifican con `ModuloService.tiene()` o `exigir()`: colores, eslogan y nombres propios (`ConfiguracionNegocioService`), segundo administrador (`UsuarioService`) y alcance de los reportes (`ReporteService.generarSegunPlan`).
- **Frontend:** `negocio.modulos` (también `window.NEGOCIO.modulos`) y el atributo `modulos` del modelo permiten ocultar o bloquear en las plantillas, por ejemplo `th:if="${modulos.contains('CARTA_QR')}"`. Ocultar no reemplaza la validación del backend.
- **Límites sugeridos.** `Plan.mesasIncluidas`, `productosIncluidos` y `usuariosIncluidos` no bloquean: `PlataformaService` calcula el uso y genera avisos para **Mi plan** y para la plataforma.

## Modelo de datos

| Entidad | Notas |
| --- | --- |
| `Pedido` | `tipo` MESA/LLEVAR, `numeroMesa` o `nombreCliente`, `estado`, `pagado`, `metodoPago`, `total`, `ultimoDetallePreparadoId`. Auditoría: `registradoPor`, `cobradoPor`, `fechaPago`. |
| `DetallePedido` | `plato`, `presentacion` (tamaño), `acompananteSopa` (acompañamiento), `cantidad` (1–99), `notas` (≤ 120), precio y subtotal. |
| `Plato` | `categoria` guarda el **código** de una `Categoria`. `disponible`, `stockDisponible` (null = sin límite), `archivado`, `imagenVersion` e `imagenUrl` calculada. |
| `PresentacionPlato` | Tamaños con precio, stock y `modalidad` (MESA, LLEVAR, AMBAS). |
| `Categoria` | `codigo` inmutable, `nombre`, `icono`, `orden`, `activa`, `llevaAcompanamiento`, `esAcompanamiento`, `cantidadSugerida`. |
| `PlatoImagen` | Bytes de la foto (`bytea`), separada de `Plato` para no cargarla en las listas. |
| `ConfiguracionNegocio` | Uno por negocio (mismo `id`): marca, colores, etiquetas, cantidad de puntos de atención, moneda, logo y Wi-Fi. |
| `Usuario` | `negocioId` (nulo solo para `SUPERADMIN`); `rol` ADMIN, MESERO, COCINA o SUPERADMIN; `activo`; `ultimoAcceso`; `debeCambiarPassword`; `passwordActualizadaEn`; `terminosVersion` y `terminosAceptadosEn`. |
| `Negocio` | Nombre, `slug` único, `plan`, `estado` (PRUEBA, ACTIVO, SUSPENDIDO, CANCELADO), `ciclo`, inicio, vencimiento, contacto, notas y `ajustesModulos` (`negocio_modulo`). |
| `Plan` | Código NORMAL, PRO o PREMIUM; precios mensual, anual y de implementación; límites sugeridos; `activo`; módulos (`plan_modulo`). |
| `LlamadoMesa` | Llamado al mesero o pedido de cuenta desde la carta: `confirmadoEn/Por` («Voy») y `atendidoEn/Por`. |
| `SolicitudPedido` | Pedido armado por el cliente en la carta: `token` para consultar su estado, `estado` PENDIENTE/APROBADA/RECHAZADA, ítems con precio de referencia y el `pedidoId` resultante. |
| `PagoParcial` | Parte de una cuenta dividida: `modo` PRODUCTOS/PARTES/MONTO, monto, método, persona e ítems pagados. `Pedido.abonado` suma lo cobrado. |
| `RegistroPlataforma` | Acciones del superadministrador (alta de negocios, suscripciones, módulos, planes). |
| `RegistroAuditoria` | Fecha, usuario, acción (`AccionAuditoria`), entidad, id y detalle legible. |

**Flujo de pedidos:** COCINA pasa PENDIENTE → EN_PREPARACION → LISTO; MESERO pasa LISTO → ENTREGADO; ADMIN puede hacer ambas cosas. El cobro es independiente del estado. Agregar productos a un pedido no pagado en EN_PREPARACION, LISTO o ENTREGADO lo devuelve a PENDIENTE, y `ultimoDetallePreparadoId` permite resaltar en cocina lo nuevo. El dominio no tiene anulación de pedidos.

**Pedidos desde la carta:** el cliente envía una `SolicitudPedido`; el mesero la aprueba (se crea el pedido, o se suma al pedido abierto de la mesa, con el mismo descuento de stock y aviso a cocina que un pedido normal, vía `ComandaService`) o la rechaza con un motivo. El cliente consulta el estado con su `token`. Los precios de la solicitud son de referencia: al aprobar se usan los vigentes.

**División de cuenta:** cada `PagoParcial` descuenta del saldo. Por productos se asignan unidades de cada línea (no se puede pagar dos veces la misma unidad); por partes iguales se cobra `total / n` y la última parte ajusta los centavos; por monto, cualquier valor hasta el saldo. Al llegar a cero el pedido queda pagado; si hubo varios métodos, `metodoPago = DIVIDIDO` y los reportes reparten el monto por método según los pagos parciales. Un pedido con pagos parciales no se cobra con `/pagar`.

**Disponibilidad dinámica:** cada producto tiene `tipoDisponibilidad`: `LIMITADA` (cantidad en `stockDisponible`, se descuenta y llega a «Agotado»), `MANUAL` (sin conteo; `pausado = true` significa «Agotado») o `CONTINUA` (siempre disponible; se puede pausar). Invariante: `LIMITADA` ⇔ `stockDisponible != null`. Reglas:
- El descuento ocurre **al confirmar** el pedido (mesero lo crea o aprueba un pedido QR) dentro de `PedidoServiceImpl` vía `DisponibilidadService.descontar`, con `UPDATE … WHERE stock >= cantidad` atómico: dos pedidos simultáneos nunca venden de más y si falta stock se revierte toda la transacción (el pedido QR queda pendiente). Si el producto es contado, también descuenta del producto aunque se elija un tamaño; los tamaños con stock propio descuentan además el suyo.
- Cada `DetallePedido` marca qué descontó (`stockDescontado`, `presentacionDescontada`, `sopaDescontada`). **Anular** (`PUT /api/pedidos/{id}/anular`, con motivo; solo sin cobro ni pagos parciales) repone exactamente esas unidades y limpia las marcas, con el pedido bloqueado: nunca se repone dos veces ni se reponen unidades que no se contaron. `ANULADO` queda fuera de activos, cocina, resumen y reportes, y no se puede cobrar ni ampliar.
- Productos pausados, agotados o fuera del menú se rechazan en el servidor (mesero y carta QR), no solo en la interfaz.
- Ajustes, pausas, cambios de modalidad y reposiciones por anulación quedan en `movimiento_disponibilidad` (quién, cuándo, antes → después, motivo).
- Permiso: autoridad `PERM_DISPONIBILIDAD` (siempre para ADMIN; para COCINA si `usuarios.gestiona_disponibilidad`). Al cambiarlo se cierran las sesiones del usuario. Las rutas `/api/disponibilidad/**` exigen además el módulo `DISPONIBILIDAD` (planes Pro y Premium); sin el módulo el stock sigue protegiendo los pedidos, pero no hay panel en vivo.
- Tiempo real: `/topic/stock` y `/topic/menu` para personal; la carta QR pública consulta cada 20 s (el WebSocket exige sesión).

**Stock:** al crear o ampliar un pedido se descuenta el stock del tamaño si la línea tiene tamaño y, si no, el del plato; también el del acompañamiento. Los cambios se publican en `/topic/stock`.

## Convenciones

- **Sin comentarios en el código** (Java, JavaScript, CSS ni SQL). Nombres claros en español en lugar de comentarios.
- **JavaScript sin frameworks.** Sin CDN: las librerías de terceros van en `static/js/vendor/` (la CSP solo permite `self`). Sin dependencias nuevas de Maven sin justificarlo. Las actuales, además de Spring Boot: `flyway` (migraciones), `zxing core` (QR en SVG), `jsvg` e `imageio-webp` (convertir logos SVG y WebP en íconos PNG de la app instalable).
- **Seguridad en el backend:** toda ruta nueva se declara en `SecurityConfig` con su rol; ocultar algo en la interfaz no es seguridad.
- **Peticiones mutantes** con `secureFetch()` (añade el token CSRF). Errores de API con `apiError()`; las respuestas de error son `{mensaje, codigo}`.
- **Nunca interpolar datos en `innerHTML` sin `escapeHtml()`**; preferir `textContent` y APIs del DOM. Nada de `onclick` con datos interpolados: usar `data-*` y delegación.
- **Sin `alert`, `confirm` ni `prompt`:** usar `showNotice(mensaje, 'ok'|'error')` y `confirmAction(mensaje, {titulo, resumen, items, total, aceptar, cancelar, peligro})`. Toda acción que cambia datos se confirma con un resumen.
- **Colores solo con tokens de `theme.css`.** No hay colores literales fuera de ese archivo (lo verifica `TemaVisualTests`). Reglas de uso:
  - Texto: `--text-main` y `--text-muted`.
  - Fondos suaves de estado: `--status-X-soft` + `--status-X-text` + `--status-X-border`.
  - Botones sólidos: `--action-primary` / `--action-accent` con su `-on`.
  - Superficies: `--surface-*`.
  - Los tokens de marca (`--brand-*`) los genera `TemaNegocio` a partir de los colores configurados, con variantes calculadas para claro y oscuro que garantizan el contraste.
- **Sistema de diseño «Moderno cálido»:** tipografía Plus Jakarta Sans alojada en `static/css/fonts/` (licencia OFL), superficies cálidas y radios/espacios/sombras como tokens (`--radius-*`, `--space-*`, `--elev-*`, `--fs-*`). La guía completa está en la skill del proyecto `.claude/skills/fepzon-ui/SKILL.md`.
- **Íconos:** sprite SVG de línea en `static/img/iconos.svg` (basado en Lucide, licencia ISC). En plantillas: `<svg th:replace="~{fragments/icono :: i('home')}"></svg>`; en JS: `Icono.crear('home')`. Los emojis quedan solo para contenido del negocio (íconos de categorías).
- **Modo claro/oscuro:** `data-theme` en `<html>`, aplicado antes de pintar desde `fragments/head.html`; la preferencia se guarda en `localStorage` (`tema`).
- **Interacción:** todos los `button` y `.btn` tienen elevación y estados hover/press globales; `.flat` los desactiva. Animaciones cortas que respetan `prefers-reduced-motion`.
- **Formularios del panel:** `Borradores.vincular(form, clave)` guarda lo escrito por usuario y formulario para no perderlo al cambiar de sección.
- **Móvil primero:** objetivos táctiles de 44 px como mínimo, campos con fuente de 16 px o más y sin desbordamiento horizontal a 360 px.

## Rutas y permisos

| Ruta | Roles |
| --- | --- |
| `/login`, `/terminos`, `/css/**`, `/js/**`, `/img/**`, `/branding/logo`, `/sw.js`, `/pwa/**` | Público |
| `/n/{slug}/login`, `/n/{slug}/logo`, `/n/{slug}/manifest.webmanifest`, `/n/{slug}/pwa/*` | Público (ingreso, logo e instalación de cada negocio) |
| `/n/{slug}/carta`, `/n/{slug}/mesa/{n}` (y las antiguas `/carta`, `/mesa/{n}`, que redirigen al negocio principal) | Público, con `CARTA_QR` |
| `/cuenta`, `/suspendido` | Cualquier usuario autenticado |
| `/superadmin`, `/superadmin/planes` | SUPERADMIN |
| `/mesero`, `/catalogo` | ADMIN, MESERO |
| `/cocina` | ADMIN, COCINA |
| `/admin/**` (inicio, menu-hoy, productos, categorias, local, carta-qr, usuarios, apariencia, plan, reportes, actividad) | ADMIN; `carta-qr` requiere `CARTA_QR` y `actividad`, `AUDITORIA` |

### API

| Método y ruta | Roles | Uso |
| --- | --- | --- |
| `GET /api/platos` | ADMIN, MESERO | Productos a la venta hoy. |
| `GET /api/platos/todos` | ADMIN | Catálogo completo. |
| `POST/PUT/DELETE /api/platos/**`, `/api/presentaciones/**` | ADMIN | Productos y tamaños. |
| `GET /api/platos/{id}/imagen` | ADMIN, MESERO, COCINA | Foto (caché inmutable con `?v=`). |
| `PUT/DELETE /api/admin/platos/{id}/imagen` | ADMIN | Subir o quitar foto (PNG, JPEG, WebP ≤ 1 MB). |
| `GET /api/categorias` | ADMIN, MESERO, COCINA | Categorías ordenadas. |
| `POST/PUT/DELETE /api/admin/categorias/**` | ADMIN | Administrar categorías; `PUT .../orden`, `DELETE ...?moverA=`. |
| `POST /api/menu-diario` | ADMIN | Publicar el menú del día. |
| `POST /api/pedidos`, `POST /api/pedidos/{id}/detalles` | ADMIN, MESERO | Crear o ampliar pedidos. |
| `PUT /api/pedidos/{id}/estado` | ADMIN, COCINA, MESERO | Cambiar estado según el flujo. |
| `PUT /api/pedidos/{id}/pagar?metodoPago=` | ADMIN, MESERO | Cobrar. |
| `GET /api/pedidos/activos`, `/api/pedidos/{id}` | ADMIN, MESERO, COCINA | Consultas. |
| `GET /api/pedidos/cocina` | ADMIN, COCINA | Tablero de cocina. |
| `GET /api/mesas/ocupadas` | ADMIN, MESERO | Mesas con pedidos abiertos. |
| `GET/PUT /api/admin/configuracion`, `PUT .../logo`, `POST .../restablecer` | ADMIN | Configuración del negocio. |
| `GET/POST/PUT/DELETE /api/admin/usuarios/**` | ADMIN | Usuarios; `PUT .../{id}/password` asigna una contraseña temporal. Cambios de rol, estado o contraseña expiran las sesiones del usuario. |
| `GET /api/admin/auditoria`, `.../acciones`, `.../usuarios` | ADMIN | Registro de actividad paginado y filtrable. |
| `GET /api/admin/resumen-hoy` | ADMIN | Resumen del día para el Inicio. |
| `GET /api/admin/reportes`, `GET /api/admin/reportes/csv?tipo=pedidos\|productos\|empleados` | ADMIN | Reportes según el plan (sin `REPORTES_COMPLETOS`, solo hoy; sin `REPORTES_AVANZADOS`, sin comparación ni horarios) y exportación CSV (`REPORTES_AVANZADOS`). |
| `GET /api/admin/plan` | ADMIN | Plan, estado, uso, avisos y módulos del negocio. |
| `GET/PUT /api/admin/configuracion/wifi`, `GET /admin/qr/mesa/{n}.svg`, `/admin/qr/carta.svg`, `/admin/qr/wifi.svg` | ADMIN, `CARTA_QR` | Wi-Fi y carteles QR. |
| `GET /api/llamados`, `PUT /api/llamados/{id}/confirmar`, `PUT /api/llamados/{id}/atender` | ADMIN, MESERO, `LLAMADOS` | Avisos de las mesas. |
| `GET /api/solicitudes`, `PUT /api/solicitudes/{id}/aprobar`, `PUT /api/solicitudes/{id}/rechazar` | ADMIN, MESERO, `PEDIDOS_QR` | Pedidos enviados desde la carta. |
| `GET /api/pedidos/{id}/cuenta`, `POST /api/pedidos/{id}/pagos` | ADMIN, MESERO, `DIVISION_CUENTA` | Cuenta dividida y pagos parciales. |
| `PUT /api/pedidos/{id}/anular` | ADMIN, MESERO | Anula un pedido sin cobro y repone las unidades contadas. |
| `GET/PUT /api/disponibilidad/**` | `PERM_DISPONIBILIDAD`, `DISPONIBILIDAD` | Panel en vivo: cantidades, pausa, modalidad e historial del día. |
| `GET /api/publico/{slug}/carta`, `…/platos/{id}/imagen`, `…/mesa/{n}/wifi` | Público, `CARTA_QR` | Carta, fotos y Wi-Fi. |
| `GET/POST /api/publico/{slug}/mesa/{n}/llamados` | Público, `LLAMADOS` | Llamar al mesero o pedir la cuenta (límite por dirección IP). |
| `POST /api/publico/{slug}/mesa/{n}/pedidos`, `GET …/pedidos/{token}` | Público, `PEDIDOS_QR` | Enviar un pedido y consultar su estado. |
| `GET /api/superadmin/negocios`, `POST /api/superadmin/negocios`, `PUT /api/superadmin/negocios/{id}`, `PUT …/{id}/suscripcion`, `PUT …/{id}/modulos`, `POST …/{id}/restablecer-acceso` | SUPERADMIN | Negocios y suscripciones. |
| `GET /api/superadmin/planes`, `PUT /api/superadmin/planes/{codigo}`, `GET /api/superadmin/modulos`, `GET /api/superadmin/registro` | SUPERADMIN | Planes, módulos e historial de la plataforma. |

**WebSocket** (`/ws`, STOMP): `/topic/cocina`, `/topic/mesero`, `/topic/stock` (`{platoId, presentacionId?, stockRestante}`) y `/topic/menu` (`ACTUALIZACION_MENU` tras cambios de menú, productos o categorías), siempre del negocio del usuario. En `/topic/mesero` también llegan `LLAMADO_MESA`, `LLAMADO_CONFIRMADO`, `LLAMADO_ATENDIDO`, `PEDIDO_QR` y `PEDIDO_QR_REVISADO`.

## Seguridad de acceso

- **Primer ingreso obligatorio:** las cuentas creadas por el sistema o por un administrador, y las contraseñas restablecidas, quedan con `debeCambiarPassword = true`. Al iniciar sesión, si falta cambiar la contraseña o aceptar la versión vigente de los términos (`Terminos.VERSION`), el login marca la sesión y `CuentaPendienteFilter` redirige toda página a `/cuenta` y responde `403 CUENTA_PENDIENTE` en la API y el WebSocket.
- **Política de contraseñas** (`PoliticaPassword`): 8 a 72 bytes, letras y números, sin espacios al inicio o al final, sin el nombre de usuario y fuera de una lista de contraseñas comunes. El navegador replica estas reglas, pero la validación que cuenta es la del servidor.
- **Contraseñas de demostración:** al arrancar, si `admin`, `mesero` o `cocina` conservan su contraseña de demostración, se marcan para cambio obligatorio. En `prod` no se arranca con `SEED_ADMIN_PASSWORD` vacía o de demostración, y `mesero`/`cocina` solo se crean si se definen sus variables.
- **Términos:** el texto está en `templates/terminos.html`. Al modificarlo de forma sustancial, sube `Terminos.VERSION` y `Terminos.VIGENTE_DESDE`: todos los usuarios deberán aceptarlo de nuevo. Cada aceptación se audita con versión e IP.
- **Cambio de contraseña propio:** exige la contraseña actual y expira las demás sesiones del usuario.
- **Recuperación:** `RECUPERAR_ACCESO=<usuario>` arranca la aplicación en un puerto aleatorio, emite una contraseña temporal para un administrador o superadministrador (si no existe, crea un administrador del negocio principal o, si es el nombre configurado en `SEED_SUPERADMIN_USERNAME` y no hay ninguno, el superadministrador), la imprime solo en la consola, la audita y termina con código 0 (2 si el usuario es del personal). No hay credenciales maestras ni accesos del proveedor.
- **Superadministrador:** se crea al arrancar con `SEED_SUPERADMIN_USERNAME` (por defecto `superadmin`) y `SEED_SUPERADMIN_PASSWORD`; en `dev` sin contraseña se genera una y se muestra una vez en el registro. Debe cambiarla en su primer ingreso. Sus acciones se guardan en `registro_plataforma`.
- **Cabeceras:** CSP (`default-src 'self'`, `frame-ancestors 'self'`, `object-src 'none'`), `X-Frame-Options`, `Referrer-Policy: same-origin`, `Permissions-Policy`, cookie de sesión `HttpOnly` y `SameSite=Lax` (`Secure` en `prod`), sesión solo por cookie y errores sin trazas.

## Reportes: definiciones

- **Ventas:** pedidos con `pagado = true` cuya fecha de cobro (`fechaPago`, o `fechaCreacion` en registros antiguos sin ella) está en el período.
- **Pedidos registrados:** pedidos creados en el período. **Por cobrar:** creados en el período y aún no pagados.
- Productos, categorías, métodos de pago, horarios y modalidades se calculan sobre las ventas cobradas, así que todos los totales cuadran con las ventas.
- Por empleado: las ventas se atribuyen a quien registró el pedido; `cobros` cuenta los pagos registrados por cada persona.
- CSV con BOM UTF-8, separador `;` y coma decimal, compatible con Excel en español; se neutralizan celdas que empiezan con `=`, `+`, `-` o `@`.

## Pruebas

```bash
./mvnw test     # .\mvnw.cmd test en Windows
```

Usan H2 en memoria y se ejecutan dentro del negocio principal (`NegocioPrincipalExtension`, registrada automáticamente). Destacan:

| Clase | Cubre |
| --- | --- |
| `SecurityAuthorizationTests` | Permisos por rol, CSRF, flujo de pedidos y bloqueo de inicio de sesión. |
| `PrimerIngresoTests` | Primer ingreso obligatorio, términos, política de contraseñas, cambio propio, restablecimiento, cabeceras de seguridad y recuperación desde el servidor. |
| `PedidoServiceImplTests` | Transiciones de estado, cobro, validación de cantidad, nota y tamaño. |
| `AdminGestionTests` | Usuarios, sesiones, auditoría y resumen del día. |
| `CategoriasYFotosTests` | Categorías, migración inicial y fotos. |
| `ReportesAvanzadosTests` | Reportes, períodos, comparación y CSV. |
| `AdminVistasTests`, `CatalogoVistaTests` | Renderizado y acceso a las páginas. |
| `TemaVisualTests` | Contraste de los tokens en claro y oscuro, variables CSS definidas y ausencia de colores literales. |
| `ConfiguracionNegocioTests` | Configuración, logo y variantes de color de marca. |
| `AislamientoHibernateTests` | Que `@TenantId` filtre consultas, `findById` y actualizaciones masivas entre negocios. |
| `MultiempresaTests` | Aislamiento por la API, superadministrador, alta de negocios, módulos por plan, límites sugeridos, suspensión, pedidos desde la carta, división de cuenta, llamados con confirmación, temas WebSocket por negocio y qué muestra cada página según el plan. |
| `CartaQrTests`, `PwaTests` | Carta pública, llamados, Wi-Fi, QR y app instalable. |

Antes de entregar un cambio: compilar, ejecutar `./mvnw test` y probar manualmente en el navegador en claro y oscuro, en computadora y en celular.

## Capturas del manual

Las imágenes de `docs/manual/img` se generan con Playwright contra una instancia con datos de ejemplo: cada marca numerada se dibuja sobre el elemento real y su texto alimenta la tabla del manual. Si cambia una pantalla, vuelve a generar la captura correspondiente para que los números sigan coincidiendo.
