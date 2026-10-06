# Citero — Backend

[![CI](https://github.com/nanopiva/citero-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/nanopiva/citero-backend/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Fly.io](https://img.shields.io/badge/deploy-Fly.io-24175B)
![License](https://img.shields.io/badge/license-MIT-blue)

API REST para la gestión de turnos de negocios de servicios (barberías, clínicas,
salones, spas, consultorios): reservas, disponibilidad, agenda por profesional,
recordatorios por email, verificación por OTP, MFA y bloqueo manual de clientes.

Demo: [citero.app](https://citero.app) · Frontend: [citero-frontend](https://github.com/nanopiva/citero-frontend) · API: `https://citero-api.fly.dev` (`GET /actuator/health`)

## Funcionalidades

**Cuentas y sesión**
- Registro con verificación por email (OTP) y login automático; access token JWT en memoria + refresh token rotativo en cookie HttpOnly.
- Roles contextuales por negocio: OWNER, STAFF y CLIENTE (registrado o invitado), expuestos como espacios de trabajo.
- Lockout por cuenta (5 fallos / 15 min) y rate limiting por IP; recuperación y cambio de contraseña con revocación de sesiones; baja de cuenta con reautenticación.
- MFA/TOTP opcional: enrolamiento, códigos de recuperación de un solo uso y desafío de login.

**Negocios**
- Alta con slug único (inmutable) y generación automática de configuración y horarios por defecto.
- Lectura pública por slug; edición parcial (solo dueño); borrado en cascada; logo/cover a Cloudinary.

**Configuración, horarios y equipo**
- Modo de reserva `PUBLIC`/`AUTHENTICATED`, tolerancia de cancelación (informativa) y visibilidad de agenda del staff.
- Horarios semanales con múltiples franjas por día y excepciones por fecha, con resolución por precedencia.
- Catálogo de servicios (duración 5–480 min, precio ≥ 0) y equipo con invitación por email y vinculación automática al registrarse.

**Disponibilidad y reservas**
- Slots en grilla de 15 min anclados a la apertura, sin cruzar franjas ni solapar turnos; asignación automática del profesional con menor carga.
- Reserva pública (modo invitado o con OTP), límites de tasa por email/negocio/IP.
- Estados `CONFIRMED → COMPLETED | NO_SHOW | CANCELLED` con transiciones validadas e idempotencia.

**Clientes y notificaciones**
- Lista negra manual por negocio (bloquear/desbloquear con motivo); sin sanciones automáticas.
- Emails asíncronos (confirmación, cancelación, invitación, OTP) con reintentos; recordatorios 24 h y 2 h antes.
- Link público firmado para gestionar el turno sin login.

**Panel**
- Métricas del negocio (turnos, ingresos estimados, ocupación, clientes bloqueados) y agenda filtrable por profesional, fecha y estado.

## Stack

Java 21 · Spring Boot 4 (Web MVC, Security, Data JPA, Validation) · PostgreSQL (Neon) / H2 ·
Flyway · JWT (JJWT) · springdoc/OpenAPI · Resend · Cloudinary · Maven · JUnit 5 + Mockito.

## Requisitos

- JDK 21
- PostgreSQL 16+ (o Docker) para el perfil `prod`; el perfil `dev` usa H2 en memoria.

## Cómo correr

### Desarrollo (H2 en memoria, sin base externa)

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Consola H2: http://localhost:8080/h2-console (`jdbc:h2:mem:citero_dev`, usuario `sa`)

El perfil `dev` incluye valores de prueba, así que arranca sin configurar nada (el OTP
se imprime en el log).

### Con PostgreSQL

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Flyway crea el esquema al arrancar. Las variables sensibles van en un archivo `.env`
(no se versiona); están todas listadas en [`.env.example`](.env.example).

## Tests

```bash
./mvnw verify
```

Corren contra H2 en memoria con valores dummy, sin necesidad de `.env` ni de una base
externa.

## Migraciones

El esquema lo gestiona Flyway (`src/main/resources/db/migration`) y Hibernate solo valida
(`ddl-auto=validate`). El esquema inicial está consolidado en `V1__init.sql`. Para un
cambio, agregá una migración **nueva** (nunca edites una ya aplicada: cambia el checksum y
la app no arranca):

```
src/main/resources/db/migration/V2__descripcion.sql
```

## Perfiles

| Perfil | Base de datos | Consola H2 | Swagger |
| ------ | ------------- | ---------- | ------- |
| `dev`  | H2 en memoria | sí | sí |
| `prod` | PostgreSQL | no | no |
| `test` | H2 en memoria | no | no |

## Estructura

```
src/main/java/com/nanopiva/citero/
├── config/       # Seguridad, CORS, Cloudinary, guard de producción
├── controller/   # Endpoints REST
├── dto/          # DTOs de request/response
├── entity/       # Entidades JPA
├── exception/    # Excepciones y manejador global
├── repository/   # Repositorios Spring Data
├── security/     # JWT, rate limiting, OTP, MFA, validación de origen
├── service/      # Lógica de negocio
└── util/
```

## API

Documentada con Swagger UI (`/swagger-ui/index.html`, habilitado en `dev`). Grupos
principales: `/api/auth`, `/api/otp`, `/api/users`, `/api/businesses` (+ `config`,
`schedules`, `schedule-exceptions`, `services`, `staff`, `clients`, `dashboard`) y
`/api/appointments`, `/api/availability`, `/api/staff/appointments`.

Autorización: endpoints públicos (registro/login, disponibilidad, reserva, catálogo,
health) y el resto autenticado. Sin token válido → `401`; autenticado sin permiso → `403`.
Toda autorización por recurso (dueño/staff/cliente) se valida en la capa de servicio.

## Seguridad

- JWT HS512 con issuer/audience/purpose fijados; refresh rotativo con detección de reuso por familia de sesión.
- Contraseñas BCrypt (12) + lista negra de comunes + chequeo contra filtraciones (HIBP k-Anonymity).
- OTP hasheados (HMAC-SHA256) en reposo; nunca se loguean secretos.
- CORS con allowlist exacta + validación de `Origin`/`Referer` en rutas de auth (anti-CSRF).
- Headers de seguridad (HSTS, `X-Content-Type-Options`, `X-Permitted-Cross-Domain-Policies`, CORP) y validación de imágenes por magic bytes.
- Rate limiting y bulkhead de concurrencia. Los stores viven **en memoria**, por lo que el backend debe correr **una sola instancia** (con varias, migrar a Redis).

## Deploy

Desplegado en **[Fly.io](https://fly.io)** (región `gru`) con base de datos **[Neon](https://neon.tech)**
(PostgreSQL serverless). La configuración está en [`fly.toml`](fly.toml).

```bash
fly deploy
```

Variables/secretos requeridos en Fly (`fly secrets set`): `DB_URL` (con `?sslmode=require`),
`DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, `CITERO_JWT_SECRET`, `CITERO_CORS_ALLOWED_ORIGINS`,
`CITERO_FRONTEND_URL`, `CITERO_FRONTEND_DASHBOARD_URL`, `CITERO_COOKIE_SECURE`,
`CITERO_COOKIE_SAME_SITE`, `RESEND_API_KEY`, `CITERO_EMAIL_FROM` y `CLOUDINARY_*`.
Ver [`.env.example`](.env.example) para la referencia completa.

## Licencia

[MIT](LICENSE)
