# Hotel Booking System

A hotel booking and room-management engine built with Spring Boot. It sells nights
of a *room type* against a per-night inventory allotment, takes payment to confirm a
held booking, and gives staff the tools to block rooms and check guests in. Access is
secured with stateless JWT authentication delivered via an `HttpOnly` cookie, with
role-based and ownership-scoped authorization, and **server-side token revocation**
(logout, password change, and admin bans) backed by Redis.

> **Documentation map**
> - **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — system overview: domain, layering, concurrency, test harness, error contract, and the technical-debt ledger.
> - **[docs/API.md](docs/API.md)** — the HTTP endpoint reference (routes, auth scope, request/response shapes, status codes).
> - **[docs/SECURITY.md](docs/SECURITY.md)** — security deep-dive: JWT internals, the auth filter, cookie attributes, authorization layers, and threat model.

---

## Features

- **Reservation lifecycle** — book a stay (creates a `PENDING` hold), confirm it by
  payment (`CONFIRMED`), check in (`CHECKED_IN`), check out (`CHECKED_OUT`). Unpaid
  holds lapse to `EXPIRED`; guests or staff can cancel a live booking (`CANCELLED`).
- **Count-based inventory** — availability is tracked as a count per room type per
  night, separate from price. Concurrent bookings for the last room are serialized
  with a **pessimistic write lock** so a room can never be oversold.
- **Day-by-day pricing** — a rate engine prices each night individually, so a stay
  spanning a rate change is charged correctly per night.
- **Payments** — confirm a hold with a payment; the pay-vs-expire race against the
  background hold sweeper is guarded by an **optimistic (`@Version`) lock**.
- **Add-ons** — guests attach catalogue extras (breakfast, parking, …) to their own
  reservation; the line price is frozen server-side.
- **Maintenance blocks** — staff take physical rooms out of service for a date range,
  excluding them from check-in assignment.
- **Stateless JWT auth** — login issues a JWT in an `HttpOnly` / `Secure` /
  `SameSite=Strict` cookie. Authorization is layered: URL rules + method
  `@PreAuthorize` (RBAC) + service-level ownership (IDOR) checks.
- **Server-side token revocation** — a two-tier Redis blacklist invalidates tokens
  before they expire: a per-token entry (logout) and a per-user entry (password
  change, admin ban) that revokes every device at once.
- **Observability** — structured logging with per-request MDC tracing
  (`requestId` / `traceId` / `userId`), a human-readable console in dev and ECS JSON
  in prod, and an `X-Request-Id` response header for client-to-log correlation.
- **Central error contract** — domain exceptions are mapped to consistent HTTP status
  codes and a structured JSON error body by a global exception handler.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL (dev &amp; prod), configured via environment variables |
| Token store | Redis (two-tier JWT revocation blacklist) |
| Security | Spring Security + JWT (`jjwt` 0.11.5), BCrypt password hashing |
| Observability | SLF4J + Logback (`logback-spring.xml`), MDC request tracing, ECS JSON in prod |
| Build | Maven (wrapper included: `./mvnw`) |
| Testing | JUnit 5, Mockito, Testcontainers (PostgreSQL + Redis) |
| Boilerplate | Lombok |

---

## Getting started

### Prerequisites
- JDK 21 or higher
- Docker + Docker Compose (for the PostgreSQL and Redis dependencies)
- No local Maven needed — the project ships the Maven Wrapper (`./mvnw`)

### 1. Configure environment

Copy the example env file and fill in the secrets:

```bash
cp .env.example .env
```

At minimum set a real `JWT_SECRET` (a long random hex string). The database and
Redis coordinates already have sensible local defaults; `.env` is read automatically
at startup (`spring.config.import: optional:file:.env`).

### 2. Start the dependencies

```bash
docker compose up -d          # PostgreSQL on :5432, Redis on :6380
```

The Postgres container initialises the schema and seed data
(`src/main/resources/db/schema.sql` and `db/data.sql`) on first boot.

### 3. Run the app

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The server starts on `http://localhost:8080` under the default `dev` profile. JPA
runs in `ddl-auto: validate` mode against the schema loaded by Postgres.

> **HTTPS note.** The auth cookie is always issued with the `Secure` flag, so a
> browser will not send it over plain `http://localhost`. Use an HTTPS context (or a
> local profile override) when exercising authenticated flows through a browser. See
> the tech-debt ledger in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#6-known-technical-debt--next-steps).

---

## Authentication in brief

1. `POST /api/auth/register` creates a guest (always `ROLE_USER`) and sets the `jwt`
   cookie. `POST /api/auth/login` does the same for an existing guest.
2. The browser (or client) then sends that cookie automatically on subsequent
   requests; the server is stateless and trusts the signed token.
3. `POST /api/auth/logout` clears the cookie **and** records the token's `jti` in the
   Redis blacklist, so a copy captured before logout is rejected on its next use.
4. `PATCH /api/auth/me/password` (self password change) and
   `POST /api/auth/admin/ban/{userId}` (admin ban) revoke **every** token for that
   user at once via a per-user blacklist entry.

`ROLE_ADMIN` accounts are **seeded, never self-registered** (see the seed data in
`db/data.sql` and the "first admin" section of [docs/SECURITY.md](docs/SECURITY.md)).
Seeded passwords are stored as BCrypt hashes.

The full route list, required roles, payloads, and status codes are in
**[docs/API.md](docs/API.md)**.

---

## Testing

```bash
./mvnw test
```

The suite (147 tests) combines three strategies:
- **Mockito unit tests** for service business logic in isolation.
- **`@DataJpaTest` slice tests** for repositories against a **Testcontainers
  PostgreSQL** database — including a real two-thread test proving the pessimistic
  booking lock.
- **`@SpringBootTest` HTTP-contract tests** that run the *actual* security chain via a
  hand-built `MockMvc`, asserting RBAC, ownership, validation, and error-status mapping
  over the wire. Tests touching token revocation run against a **Testcontainers Redis**.

Testcontainers spins the PostgreSQL and Redis containers up automatically, so `./mvnw
test` needs only a running Docker daemon — no manually provisioned test database.

See the test-harness section of [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#resilient-test-harness) for why the harness is wired this way.

---

## Observability

Logging is configured in [`src/main/resources/logback-spring.xml`](src/main/resources/logback-spring.xml)
with two profile-specific philosophies:

- **dev** — a human-readable console line with the trace keys inlined
  (`[requestId|userId]`), and the application package at `DEBUG` so flow-level
  statements are visible.
- **prod** — newline-delimited **ECS JSON** on stdout (via Spring Boot 4.1's native
  `StructuredLogEncoder`, no logstash dependency), ready for a log shipper. The
  application package logs at `INFO`; SQL and framework noise stay at `WARN`.

Every request thread carries three MDC keys, populated by
[`MdcLoggingFilter`](src/main/java/com/Abdelwahab/RoomBooking/config/MdcLoggingFilter.java)
(runs at highest precedence, cleared in a `finally`):

- `requestId` — one id per HTTP request, echoed back in the `X-Request-Id` response
  header so a client-reported failure maps to exact server log lines.
- `traceId` — cross-service correlation id; honours an inbound `X-Trace-Id`.
- `userId` — the authenticated guest id, set by `JwtAuthenticationFilter` once the
  token is verified.

Log-level discipline: **INFO** for business events (booking created, payment
confirmed, hold expired, token revoked, admin ban), **DEBUG** for write-endpoint entry
and non-trivial flow, and pure reads stay quiet. Failures are logged in exactly one
place — `GlobalExceptionHandler` — so nothing is double-logged: unmapped 500s at
**ERROR** with a stack trace, business conflicts (409) and security events (failed
login, access denied) at **WARN**, and expected client errors (404, bad-request 400)
at **DEBUG**. Full objects and secrets (passwords, raw tokens) are never logged.

---

## Project layout

```
src/main/java/com/Abdelwahab/RoomBooking/
├── controller/   # HTTP adapters — bind, validate, delegate, map to status codes
├── service/      # business logic, transaction boundaries, state machine, locking
├── repository/   # Spring Data JPA + hand-written JPQL (availability, locking, sums)
├── model/        # JPA entities + enums (never serialized to clients directly)
├── dto/          # request/response wire shapes with validation constraints
├── security/     # JWT filter, JwtService, SecurityConfig, Redis revocation, auth beans
├── config/       # cross-cutting config — MdcLoggingFilter (request tracing)
└── exception/    # domain exceptions + GlobalExceptionHandler (the error contract)
```

---

## Known limitations

Tracked honestly in the [technical-debt ledger](docs/ARCHITECTURE.md#6-known-technical-debt--next-steps).
Highlights: there is no CORS configuration yet, and payment is a state transition
rather than a real gateway integration.
Interactive API docs (SpringDoc/OpenAPI) are deferred pending Spring Boot 4 / Spring 7
compatibility; until then, [docs/API.md](docs/API.md) is the endpoint reference.
