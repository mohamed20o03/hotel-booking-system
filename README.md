# Room Booking API

A hotel booking and room-management engine built with Spring Boot. It sells nights
of a *room type* against a per-night inventory allotment, takes payment to confirm a
held booking, and gives staff the tools to block rooms and check guests in. Access is
secured with stateless JWT authentication delivered via an `HttpOnly` cookie, with
role-based and ownership-scoped authorization.

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
- **Central error contract** — domain exceptions are mapped to consistent HTTP status
  codes and a structured JSON error body by a global exception handler.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7) |
| Persistence | Spring Data JPA / Hibernate |
| Database | H2 (in-memory) |
| Security | Spring Security + JWT (`jjwt` 0.11.5), BCrypt password hashing |
| Build | Maven (wrapper included: `./mvnw`) |
| Boilerplate | Lombok |

---

## Getting started

### Prerequisites
- JDK 21 or higher
- No local Maven needed — the project ships the Maven Wrapper (`./mvnw`)

### Run

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The server starts on `http://localhost:8080`. The schema and seed data
(`src/main/resources/db/schema.sql` and `db/data.sql`) load automatically on startup;
JPA runs in `ddl-auto: validate` mode against that schema.

### Supporting tools
- **H2 console** — `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:roombookingdb`, user `sa`, empty password).

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
3. `POST /api/auth/logout` clears the cookie.

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

The suite (127 tests) combines three strategies:
- **Mockito unit tests** for service business logic in isolation.
- **`@DataJpaTest` slice tests** for repositories against a context-isolated H2
  database — including a real two-thread test proving the pessimistic booking lock.
- **`@SpringBootTest` HTTP-contract tests** that run the *actual* security chain via a
  hand-built `MockMvc`, asserting RBAC, ownership, validation, and error-status mapping
  over the wire.

See the test-harness section of [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#resilient-test-harness) for why the harness is wired this way.

---

## Project layout

```
src/main/java/com/Abdelwahab/RoomBooking/
├── controller/   # HTTP adapters — bind, validate, delegate, map to status codes
├── service/      # business logic, transaction boundaries, state machine, locking
├── repository/   # Spring Data JPA + hand-written JPQL (availability, locking, sums)
├── model/        # JPA entities + enums (never serialized to clients directly)
├── dto/          # request/response wire shapes with validation constraints
├── security/     # JWT filter, JwtService, SecurityConfig, auth beans
└── exception/    # domain exceptions + GlobalExceptionHandler (the error contract)
```

---

## Known limitations

Tracked honestly in the [technical-debt ledger](docs/ARCHITECTURE.md#6-known-technical-debt--next-steps).
Highlights: JWTs are not server-side revocable (logout is client-side only),
unauthenticated requests currently return `403` rather than `401` (no
`AuthenticationEntryPoint`), there is no CORS configuration yet, and the JWT signing
secret lives in `application.yaml` (should be externalized per environment).
Interactive API docs (SpringDoc/OpenAPI) are deferred pending Spring Boot 4 / Spring 7
compatibility; until then, [docs/API.md](docs/API.md) is the endpoint reference.
