# CLAUDE.md — LDAPAdmin

## Project Overview

LDAPAdmin is an enterprise LDAP directory management platform. Java 21 / Spring Boot 3.3.4 backend with a Vue 3 frontend. PostgreSQL 16 for persistence, UnboundID LDAP SDK for directory operations.

## Build & Run

### Backend (Java/Maven)

```bash
./mvnw clean package          # Build JAR (includes tests)
./mvnw test                   # Run tests only
./mvnw spring-boot:run        # Run locally (needs PostgreSQL)
```

### Frontend (Vue/Vite)

```bash
cd frontend
npm install
npm run dev                   # Dev server on port 5173
npm run build                 # Production build
```

### Docker Compose (full stack)

```bash
cp .env.example .env          # Fill in ENCRYPTION_KEY, JWT_SECRET, BOOTSTRAP_SUPERADMIN_PASSWORD
docker-compose up --build     # App on :8080, DB on :5432
```

## Testing

```bash
./mvnw test                   # All backend tests (34 test files)
./mvnw test -Dtest=ClassName  # Single test class
```

No frontend test runner is configured yet.

## Project Structure

```
src/main/java/com/ldapadmin/
  auth/          # JWT auth, feature permissions, security aspects
  config/        # Spring config (Security, Flyway, OpenAPI)
  controller/    # REST endpoints (~30 controllers)
  service/       # Business logic (~29 services)
  repository/    # Spring Data JPA repositories
  entity/        # JPA entities and enums
  dto/           # Request/response DTOs
  ldap/          # LDAP operations (users, groups, schema, changelog)
  exception/     # Custom exceptions
  util/          # Utilities

frontend/src/
  api/           # Axios API client layer
  components/    # Reusable Vue components
  composables/   # Vue composables
  stores/        # Pinia state stores
  views/         # Page components (~23 views)
  router/        # Vue Router config

src/main/resources/
  application.yml              # Spring Boot config
  db/migration/                # Flyway migrations (V1–V36)
```

## Key Architecture Decisions

- **Auth**: JWT-based stateless auth. `@RequiresFeature` annotation + AOP for feature-level authorization. Roles: SUPERADMIN, ADMIN, READ_ONLY.
- **Database**: Flyway migrations manage schema. Never edit existing migration files — always add new ones.
- **LDAP**: UnboundID SDK via `LdapConnectionFactory`. Connection pooling enabled.
- **Frontend state**: Pinia stores. API calls go through `frontend/src/api/`.
- **DTOs**: Separate DTO classes for all API contracts — don't expose entities directly.

## Environment Variables

Required secrets (no defaults):
- `ENCRYPTION_KEY` — Base64 AES-256 key
- `JWT_SECRET` — Base64 random secret
- `BOOTSTRAP_SUPERADMIN_PASSWORD` — Initial admin password

See `.env.example` for the full list.

## Common Conventions

- Lombok annotations on entities and DTOs (`@Data`, `@Builder`, etc.)
- Controllers return `ResponseEntity<>`
- Service methods throw custom exceptions from `exception/` package
- Frontend components use Vue 3 Composition API with `<script setup>`
- Tailwind CSS 4 for styling
