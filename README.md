# Sistema de Gestión Comercial

Sistema para tomar pedidos, prepararlos, entregarlos y cobrarlos, pensado para restaurantes y cualquier negocio que atienda en mesas, mostrador o para llevar. Funciona como servicio **multiempresa**: una sola instalación atiende a varios negocios con datos separados, cada uno con su plan (Normal, Pro o Premium), su marca, su moneda, sus usuarios y sus módulos.

## Qué incluye

- **Atención (mesero):** pensada para el celular. Pedidos por mesa o para llevar, con cantidades, tamaños, acompañamientos y notas; catálogo con fotos para el cliente; cobro y entrega.
- **Preparación (cocina):** tablero de pendientes, en preparación y listos, en tiempo real.
- **Administración:** resumen del día, menú de hoy, productos con foto, categorías, mesas, usuarios, reportes, registro de actividad y apariencia.
- **Reportes:** ventas por período con comparación, productos, equipo, métodos de pago, horarios y exportación a Excel (CSV).
- **Carta digital y mesa (Pro):** carta con QR por mesa, Wi-Fi visible, «Llamar al mesero» y «Pedir la cuenta» con confirmación del mesero, pedidos desde la carta que el mesero aprueba antes de enviarlos a cocina, y división de la cuenta por productos, personas o monto.
- **App instalable (Pro):** el sistema se instala en celulares y tablets del personal (PWA, requiere HTTPS).
- **Plataforma:** panel de superadministrador para dar de alta negocios, asignar planes, activar, suspender o cambiar suscripciones y ajustar módulos por negocio.
- **Seguridad:** contraseñas temporales que se cambian en el primer ingreso, política de contraseñas, aceptación de Términos y condiciones, bloqueo por intentos fallidos, roles, CSRF, cabeceras de seguridad, auditoría y recuperación de acceso solo desde el servidor.

## Documentación

| Documento | Para quién |
| --- | --- |
| [Manual de usuario](docs/MANUAL-DE-USUARIO.md) ([PDF](docs/Manual-de-Usuario.pdf)) | Dueño, administradores y personal: cada pantalla con capturas numeradas. |
| [Plataforma](docs/PLATAFORMA.md) | Superadministrador: negocios, planes, módulos y suscripciones. |
| [Instalación y mantenimiento](docs/INSTALACION.md) | Quien instala y entrega el sistema: Docker, producción, respaldos, actualizaciones y recuperación de acceso. |
| [Desarrollo](docs/DESARROLLO.md) | Programadores: arquitectura, convenciones, API y pruebas. |

## Levantar el proyecto con Docker

Requisitos: [Git](https://git-scm.com/) y [Docker Desktop](https://www.docker.com/products/docker-desktop/) abierto.

```bash
git clone https://github.com/Kiwineitor/Sistema-de-Gesti-n-Comercial.git
cd Sistema-de-Gesti-n-Comercial
docker compose up -d --build
```

Abre `http://localhost:8080` (la primera vez tarda unos minutos en compilar). No hace falta crear `.env`: Docker incluye su base de datos PostgreSQL y un menú de ejemplo. Al arrancar, las migraciones de Flyway crean o actualizan la base; una instalación anterior conserva todos sus datos en el negocio principal.

### Credenciales de primer arranque

| Usuario | Contraseña temporal | Pantalla |
| --- | --- | --- |
| `admin` | `admin123` | Administración |
| `mesero` | `mesero123` | Atención |
| `cocina` | `cocina123` | Preparación |
| `superadmin` | `superadmin123` | Plataforma (negocios y planes) |

Los tres primeros pertenecen al negocio **principal** (plan Premium); ingresan en `http://localhost:8080/login` o `http://localhost:8080/n/principal/login`. Solo existen cuando la base se crea vacía. En el primer ingreso cada cuenta **debe** crear su propia contraseña (mínimo 8 caracteres, letras y números, sin el nombre de usuario) y aceptar los Términos y condiciones.

### Actualizar cuando hay cambios en el repositorio

```bash
git pull
docker compose up -d --build
```

Los datos se conservan entre actualizaciones. Para empezar de cero con la base vacía y las credenciales de primer arranque: `docker compose down -v` (borra todos los datos) y luego `docker compose up -d --build`.

### Comandos útiles

| Comando | Para qué |
| --- | --- |
| `docker compose ps` | Ver si los contenedores están corriendo. |
| `docker compose logs -f app` | Ver el registro de la aplicación. |
| `docker compose stop` / `docker compose start` | Detener o reanudar sin perder datos. |
| `docker compose run --rm -e RECUPERAR_ACCESO=admin app` | Emitir una contraseña temporal si ningún administrador puede entrar. |

Si el puerto 8080 está ocupado, crea un `.env` con `APP_PORT=8085` (u otro libre). La instalación para un negocio real (perfil `prod`) está en [Instalación y mantenimiento](docs/INSTALACION.md#3-instalación-para-el-negocio-entrega-al-cliente).

## Roles

| Rol | Qué ve |
| --- | --- |
| Administrador (`ADMIN`) | Todo: administración, catálogo, atención y preparación. |
| Atención (`MESERO`) | Atención y catálogo: toma pedidos, cobra y entrega. |
| Preparación (`COCINA`) | Preparación. |
| Superadministrador (`SUPERADMIN`) | Solo la plataforma: negocios, planes y suscripciones. No ve los datos de trabajo de los negocios. |
| Cliente (sin cuenta) | Con `CARTA_QR`: la carta del negocio. Con `LLAMADOS` y `PEDIDOS_QR`, desde su mesa: llamar al mesero, pedir la cuenta y enviar pedidos para aprobación. |

Los nombres que ve el personal («Mesero», «Cocina», «Mesa»…) se cambian en **Administración → Mi local**.

## Stack

Java 21 · Spring Boot 4 · Spring Security · Spring Data JPA (Hibernate 7, multiempresa con `@TenantId`) · Flyway · PostgreSQL 16 · Thymeleaf con HTML, CSS y JavaScript sin frameworks · WebSocket STOMP/SockJS · Maven Wrapper · Docker Compose.

## Desarrollo local sin Docker

```bash
./mvnw spring-boot:run        # requiere PostgreSQL y las variables PG*
./mvnw test                   # usa H2, no necesita PostgreSQL
```

En Windows usa `.\mvnw.cmd`. Más detalles en [Desarrollo](docs/DESARROLLO.md).
