# Plataforma: negocios, planes y módulos

Guía para quien opera el sistema como servicio (SaaS): el **superadministrador**. Explica cómo se dan de alta los negocios, cómo funcionan los planes Normal, Pro y Premium y cómo se activan, suspenden o cambian las suscripciones. Aún no hay pasarela de pagos: los cobros se registran fuera del sistema y la suscripción se actualiza a mano desde este panel.

## 1. Conceptos

| Concepto | Qué es |
| --- | --- |
| **Negocio** | Un restaurante o local. Tiene sus propios productos, categorías, pedidos, usuarios, reportes, registro de actividad, logo, colores, moneda, nombres («Mesa», «Mesero»…) y Wi-Fi. Ningún usuario de un negocio puede ver ni modificar datos de otro. |
| **Dirección** (`slug`) | Identificador corto del negocio en las direcciones públicas: `/n/<dirección>/login`, `/n/<dirección>/carta` y `/n/<dirección>/mesa/<número>`. Cambiarla invalida los QR ya impresos. |
| **Plan** | Normal, Pro o Premium. Define los módulos incluidos, los precios y los límites sugeridos. |
| **Módulo** | Una función que se activa o desactiva según el plan (por ejemplo, la carta QR). Se puede ajustar por negocio. |
| **Suscripción** | Plan, estado (en prueba, activo, suspendido o cancelado), ciclo de facturación y fecha de vencimiento de un negocio. |
| **Superadministrador** | Usuario de la plataforma (rol `SUPERADMIN`). No pertenece a ningún negocio y no ve sus pantallas de trabajo; solo administra negocios y planes. |

## 2. Planes y módulos

Los planes vienen cargados según la propuesta comercial y se pueden editar en **Planes** (precios, descripción, límites y módulos). Los cambios de módulos de un plan se aplican al momento a todos sus negocios.

| Módulo | Qué habilita | Normal | Pro | Premium |
| --- | --- | :---: | :---: | :---: |
| Operación base | Pedidos en mesa y para llevar, cocina en tiempo real, menú del día, cobro, productos con foto, nombre, logo y moneda, reporte del día | ✓ | ✓ | ✓ |
| `APARIENCIA_AVANZADA` | Colores, eslogan y nombres propios («Mesa» o «Box», «Mesero» o «Garzón») | — | ✓ | ✓ |
| `APP_INSTALABLE` | Instalar el sistema como app (PWA) en celulares y tablets | — | ✓ | ✓ |
| `CARTA_QR` | Carta pública, QR por mesa y Wi-Fi visible | — | ✓ | ✓ |
| `LLAMADOS` | «Llamar al mesero» y «Pedir la cuenta» desde la mesa, con confirmación del mesero | — | ✓ | ✓ |
| `PEDIDOS_QR` | El cliente arma su pedido en la carta y el mesero lo aprueba antes de enviarlo a cocina | — | ✓ | ✓ |
| `DIVISION_CUENTA` | Cobrar una cuenta por productos, por partes iguales o por monto, con varios métodos de pago | — | ✓ | ✓ |
| `REPORTES_COMPLETOS` | Reportes por día, semana y mes, productos más vendidos y ventas por persona | — | ✓ | ✓ |
| `REPORTES_AVANZADOS` | Comparación con el período anterior, horarios y exportación a Excel | — | — | ✓ |
| `AUDITORIA` | Pantalla de registro de actividad | — | — | ✓ |
| `VARIOS_ADMINISTRADORES` | Más de un administrador activo | — | — | ✓ |
| `CARTA_SIN_MARCA` | La carta no muestra «Carta digital con Gestión Comercial» | — | — | ✓ |

La actividad se registra siempre, también en Normal y Pro. Solo la pantalla para consultarla depende del módulo, así que al subir a Premium el historial ya está completo.

### Límites sugeridos

Mesas, productos y usuarios **no se bloquean**. Normal incluye 10 mesas, 50 productos y 3 usuarios; Pro, 30 mesas, 200 productos y usuarios sin límite; Premium no tiene límites. Cuando un negocio los supera:

- el sistema sigue funcionando con normalidad;
- el administrador del negocio ve el uso y el aviso en **Mi plan**;
- el superadministrador ve el aviso en la tarjeta del negocio, para ofrecer el cambio de plan.

## 3. Ingresar como superadministrador

El usuario se crea al arrancar el sistema la primera vez:

- **Pruebas (Docker):** `superadmin` / `superadmin123`. Igual que las demás cuentas, se pide una contraseña propia en el primer ingreso.
- **Producción:** define `SEED_SUPERADMIN_PASSWORD` (y, si quieres, `SEED_SUPERADMIN_USERNAME`) antes del primer arranque. Si no lo hiciste, emite una contraseña temporal desde el servidor con `RECUPERAR_ACCESO=superadmin` (ver [Instalación](INSTALACION.md#9-recuperar-el-acceso-de-un-administrador)).

Al ingresar se abre **Plataforma → Negocios**.

## 4. Dar de alta un negocio

1. **Negocios → Nuevo negocio.**
2. Escribe el nombre. La dirección y el usuario administrador se sugieren solos (`mi-negocio`, `admin.mi-negocio`); puedes cambiarlos.
3. Elige el plan, el estado inicial (**En prueba** pone el vencimiento a 14 días) y la facturación.
4. **Crear negocio.** El sistema crea la configuración, las categorías iniciales y el administrador, y muestra:
   - la dirección de ingreso (`/n/<dirección>/login`),
   - el usuario,
   - una **contraseña temporal que no se vuelve a mostrar**.
5. Entrega esos datos al dueño. En su primer ingreso deberá crear su contraseña y aceptar los términos. Desde ahí él mismo crea a su personal en **Usuarios**.

## 5. Gestionar un negocio

En la tarjeta del negocio, **Gestionar**:

| Pestaña | Para qué |
| --- | --- |
| **Suscripción** | Cambiar el plan, el estado, el ciclo y las fechas. **Generar contraseña temporal del administrador** sirve para dar soporte cuando el dueño perdió el acceso. |
| **Módulos** | Activar o desactivar módulos puntuales para ese negocio, por encima de lo que dice el plan (por ejemplo, dar la carta QR a un cliente Normal como promoción). |
| **Datos** | Nombre, dirección, contacto y notas internas. |
| **Historial** | Cambios de plan, estado y módulos hechos desde la plataforma, con fecha y autor. |

### Estados de la suscripción

| Estado | Efecto |
| --- | --- |
| En prueba | Acceso completo. El administrador ve en **Mi plan** cuándo termina la prueba y, en la última semana, un aviso en todo el panel. |
| Activo | Acceso completo. Si hay fecha de vencimiento, siete días antes el administrador ve un aviso. |
| Suspendido | Se cierran las sesiones del personal, nadie puede ingresar (ven «Servicio suspendido») y la carta pública deja de responder. **Los datos se conservan.** |
| Cancelado | Igual que suspendido. Se usa cuando el cliente deja el servicio. |

El vencimiento no suspende automáticamente: avisa al negocio y a la plataforma, y la decisión de suspender es manual.

Todos los cambios quedan en el historial de la plataforma. Los cambios de suscripción y las contraseñas temporales emitidas por soporte también aparecen en la **Actividad** del negocio, con el usuario «soporte».

## 6. El negocio que ya existía

Al actualizar una instalación anterior, sus datos se asignan al negocio **principal** (dirección `principal`) con plan **Premium**, para que no pierda ninguna función. Las direcciones antiguas `/carta` y `/mesa/<número>` redirigen a la carta de ese negocio.
