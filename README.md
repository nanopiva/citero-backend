# Citero — Backend

[![CI](https://github.com/nanopiva/citero-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/nanopiva/citero-backend/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![License](https://img.shields.io/badge/license-MIT-blue)

API REST para la gestión de turnos de negocios de servicios (barberías, clínicas, salones,
spas, consultorios): reservas, agenda por profesional, disponibilidad, recordatorios por
email, verificación por OTP y reputación de clientes.

Demo: [citero.app](https://citero.app) · Frontend: [citero-frontend](https://github.com/nanopiva/citero-frontend)

## Funcionalidades

- Autenticación con JWT y refresh token rotativo en cookie segura.
- Reservas en modo público, registrado o con verificación por OTP, sin dobles reservas.
- Disponibilidad calculada por día y por profesional, según los horarios del negocio.
- Gestión de negocios, servicios, equipo, horarios y reglas de reserva.
- Invitación de profesionales por email, con vinculación automática al crear su cuenta.
- Recordatorios automáticos por email 24 h y 2 h antes del turno.
- Reputación de clientes con strikes y bloqueo automático.
- Panel con métricas de turnos e ingresos, y subida de imágenes.
- Límite de intentos en login y OTP, y manejo centralizado de errores.

## Stack

Java 21 · Spring Boot 4 (Web MVC, Security, Data JPA, Validation) · PostgreSQL / H2 ·
Flyway · JWT (JJWT) · springdoc/OpenAPI · Resend · Cloudinary · Maven · JUnit 5 + Mockito.

## Requisitos

- JDK 21
- PostgreSQL 16 (o Docker)

## Cómo correr

### Desarrollo (H2 en memoria, sin base externa)

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Consola H2: http://localhost:8080/h2-console (`jdbc:h2:mem:citero_dev`, usuario `sa`)

El perfil `dev` incluye valores de prueba, así que arranca sin configurar nada.

### Con PostgreSQL

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Flyway crea el esquema al arrancar. Las variables sensibles van en un archivo `.env`
(no se versiona); están todas listadas en [`.env.example`](.env.example).

## Tests

```bash
./mvnw test
```

Corren contra H2 en memoria con valores dummy, sin necesidad de `.env` ni de una base externa.

## Migraciones

El esquema lo gestiona Flyway (`src/main/resources/db/migration`) y Hibernate solo valida
(`ddl-auto=validate`). Para un cambio, agregá una migración nueva:

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
├── config/       # Seguridad, CORS, Cloudinary
├── controller/   # Endpoints REST
├── dto/          # DTOs de request/response
├── entity/       # Entidades JPA
├── exception/    # Excepciones y manejador global
├── repository/   # Repositorios Spring Data
├── security/     # JWT y rate limiting
├── service/      # Lógica de negocio
└── util/
```

## API

Documentada con Swagger UI (`/swagger-ui/index.html`, habilitado en `dev`). Principales
grupos: `/api/auth`, `/api/businesses`, `/api/appointments`, `/api/availability` y
`/api/users`.

## Licencia

[MIT](LICENSE)
