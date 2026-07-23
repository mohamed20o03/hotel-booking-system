# Architecture — System Overview

> Engineering-level overview of the Room Booking backend: what it does, how the
> non-obvious parts hang together, and where the current trade-offs are.
> For the security internals (JWT byte layout, filter walk-through, cookie
> attributes, threat model) see the companion deep-dive in [SECURITY.md](SECURITY.md);
> this document deliberately does not repeat that material.

---

## 1. Core business domain

A **Hotel Booking & Room Management Engine**. It sells nights of a *room type*
(not a specific physical room) against a per-day inventory allotment, takes
payment to confirm a held booking, and lets staff block rooms and check guests
in. Four workflows carry the domain:

- **Booking lifecycle.** A guest requests a stay → the system holds inventory and
  creates a `PENDING` reservation with an expiry → payment moves it to
  `CONFIRMED` → front-desk **check-in** assigns a physical room and moves it to
  `CHECKED_IN` → later `CHECKED_OUT`. Unpaid holds lapse to `EXPIRED`; a guest or
  admin may `CANCELLED` a live booking.
- **Inventory & pricing.** Availability is a **count** per room-type per night,
  separate from price. A day-by-day rate engine sums nightly rates over the stay,
  so a single booking spanning a rate boundary is priced correctly per night.
- **Maintenance blocking.** Admins take physical rooms out of service for a date
  range; blocked rooms are excluded from check-in assignment and availability.
- **Payment processing.** Payment is the state transition that confirms a hold and
  clears its expiry, guarded against the concurrent expiry sweep (see §3).

### Reservation state machine

```
                 pay                 check-in              check-out
  PENDING ───────────────▶ CONFIRMED ─────────▶ CHECKED_IN ─────────▶ CHECKED_OUT
     │                         │
     │ hold expires            │ cancel (owner/admin)
     ▼                         ▼
  EXPIRED                  CANCELLED
```

`NO_SHOW` is a terminal status for a confirmed guest who never arrives.
Only `PENDING` and `CONFIRMED` reservations hold inventory, so only those two are
cancellable; every other transition is rejected at the service layer as an invalid
state change (surfaced as `400`, see §5).

---

## 2. Layered structure

Standard Spring layering, with the responsibility boundaries that matter for this
codebase:

- **Controller** — HTTP wire contract only: bind/validate the request, delegate,
  map the result to a status code. No business logic. Ownership/RBAC decisions are
  *asserted* here (via URL rules and `@PreAuthorize`) but *enforced* deeper.
- **Service** — the transaction boundary and the home of all business invariants:
  state-machine rules, pricing, inventory math, ownership (IDOR) checks.
- **Repository** — Spring Data JPA. Hand-written JPQL only where a derived query
  can't express the intent (availability's double `NOT EXISTS`, the pessimistic
  inventory lock, the payment-sum aggregate).
- **Model** — JPA entities; never serialized directly to clients. Every endpoint
  speaks in DTOs.
- **Security** — a stateless JWT filter in front of the chain; see [SECURITY.md](SECURITY.md).

---

## 3. Key architectural highlights

### Security & identity guardrails
- **Stateless JWT in an `HttpOnly` cookie.** The token rides in a cookie named
  `jwt` with `HttpOnly` (no JS access → XSS can't exfiltrate it), `Secure`
  (HTTPS-only), `SameSite=Strict` (no cross-site send → CSRF surface removed, which
  is why CSRF is disabled in the chain), and a 24h `Max-Age`. Sessions are
  `STATELESS`.
- **Dual-layer authorization.** *Layer 1* — URL/verb rules in `SecurityConfig`
  (public reads, admin-only writes, everything else authenticated). *Layer 2* —
  method-level `@PreAuthorize("hasRole('ADMIN')")` on staff actions such as
  check-in. *Layer 3* — **ownership / IDOR** checks in the service (a guest may
  only cancel or read *their own* reservation), which no role gate can express.
  Authority comes from the database (the `Guest` row), not from a claim in the
  token.

> Note: CORS is configured via a `CorsConfigurationSource` bean in `SecurityConfig`,
> with allowed origins externalized to `${CORS_ALLOWED_ORIGINS}`. `allowCredentials(true)`
> is set with explicit origins (never `*`) so the JWT cookie is sent cross-origin.

- **Auth rate limiting.** `RateLimitFilter` sits in the security chain ahead of the
  credential check and throttles `POST /api/auth/login` and `/register` per client
  IP, using a Redis fixed-window counter in `RateLimitService` (`INCR` + first-hit
  `EXPIRE`, default 10 attempts / 60s, externalized to `${RATE_LIMIT_AUTH_*}`).
  Excess attempts get `429` with a `Retry-After` header and the standard
  `ErrorResponse` body. It **fails open** — the deliberate inverse of the revocation
  gate — because a Redis outage must throttle nobody rather than lock everybody out
  of login. The submitted credentials are never logged.

### Concurrency & data integrity
Two independent race conditions are handled with two different locks, deliberately:

- **Overselling the last room (pessimistic).** Concurrent bookings for the same
  room-type/night are serialized by a `SELECT … FOR UPDATE` in
  `RoomTypeInventoryRepository.lockForStay` (`@Lock(PESSIMISTIC_WRITE)`). The
  second transaction blocks on the row until the first commits, then re-reads the
  now-incremented count and loses. Without the lock both would read the same free
  count and both write a booking. This is proven by a real two-thread test against
  a live PostgreSQL connection (Testcontainers), not a mock.
- **Pay-vs-expire race (optimistic).** A `@Version` column on `Reservation` guards
  the hold. If the hold-expiry sweeper and an incoming payment touch the same
  `PENDING` reservation at once, the optimistic-lock check turns the loser into a
  clean, retryable failure instead of confirming an already-expired booking (or
  expiring an already-paid one).
- **Unique constraints** back the invariants at the schema level (e.g. hotel
  `phone`/`email`), so integrity survives even a logic bug above.

### Resilient test harness
- **Layer-isolated unit tests** mock collaborators (`@MockitoBean`) and assert one
  layer's logic — fast, no context.
- **HTTP-layer contract tests** use `@SpringBootTest` with a **hand-built
  `MockMvc` wired via `.apply(springSecurity())`**. This is load-bearing: under
  Spring Boot 4.1's auto-configured `MockMvc`, `@WithMockUser` was *not* propagated
  into the filter chain, so every request arrived anonymous and all authenticated
  cases returned 403. The manual wiring runs the **actual** production security
  rules, so RBAC/ownership assertions mean what they say.
- **Testcontainers for all integration tests.** A shared `AbstractIntegrationTest`
  base class starts a `PostgreSQLContainer` and a `RedisContainer` once per JVM in a
  `static` initialiser (not `@Container`, which would stop them on `@DirtiesContext`
  reloads). `@DynamicPropertySource` injects the random ports before any
  `ApplicationContext` is created. Repository tests, controller tests, and the Redis
  revocation integration test all extend this base — no H2, no mocks for the DB or
  cache layer.
- **152 tests across 25 test files**, covering unit, repository, controller, security,
  and concurrency scenarios.

### Error handling & web contracts
A single `@RestControllerAdvice` (`GlobalExceptionHandler`) maps domain exceptions
to HTTP status codes, so controllers never build error bodies. The mapping is the
external contract:

| Exception (thrown in service)                                   | HTTP status | Meaning on the wire                    |
|-----------------------------------------------------------------|-------------|----------------------------------------|
| `MethodArgumentNotValidException` (`@Valid`) / malformed JSON   | **400**     | Bad request body                       |
| `IllegalArgumentException` (invalid state transition, IDOR)     | **400**     | Business precondition failed           |
| `DateTimeParseException` (malformed date query param)           | **400**     | Bad request parameter                  |
| `AuthenticationException` (bad login credentials)               | **401**     | Not authenticated                      |
| `AccessDeniedException` (RBAC via URL rule / `@PreAuthorize`)   | **403**     | Authenticated but not permitted        |
| `ResourceNotFoundException`                                     | **404**     | No such resource                       |
| `NoAvailabilityException` / `DuplicateResourceException` / `PaymentException` | **409** | Business state conflict     |
| any other `Exception`                                           | **500**     | Unexpected                             |

Every response body is a uniform `ErrorResponse` (`timestamp`, `status`, `error`,
`message`, `path`).

> **Contract accuracy note (401 vs 403).** A `RestAuthenticationEntryPoint` is
> registered, so an **unauthenticated** request to a protected endpoint returns
> **401** (verified by the controller tests), while an **authenticated** caller who
> lacks the required role returns **403**. The two failure modes are distinguishable
> by clients.

### Observability & structured logging
Logging is treated as a first-class cross-cutting concern, configured entirely in
`logback-spring.xml` and threaded through every request by an MDC filter.

- **Per-request MDC tracing.** `config/MdcLoggingFilter` runs at
  `Ordered.HIGHEST_PRECEDENCE` (ahead of the security chain, so even a rejected
  request is traceable) and stamps three keys onto the request thread: `requestId`
  (one per request, echoed back as the `X-Request-Id` response header), `traceId`
  (cross-service correlation; honours an inbound `X-Trace-Id`), and `userId` (added
  by `JwtAuthenticationFilter` once the token verifies). The MDC is cleared in a
  `finally` block — servlet threads are pooled, and a stale `userId` leaking into the
  next request's logs would corrupt any investigation built on them.
- **Profile-specific output.** *dev* → a colourised console line with
  `[requestId|userId]` inlined and the application package at `DEBUG`. *prod* →
  newline-delimited **ECS JSON** via Spring Boot 4.1's native `StructuredLogEncoder`
  (no logstash-encoder dependency), with MDC keys promoted to top-level fields so a
  shipper can forward to Elasticsearch/Loki/CloudWatch without a regex parse. SQL and
  framework loggers are pinned to `WARN` in prod to keep volume down.
- **Level discipline.** `INFO` marks state-changing business events (booking created,
  payment confirmed, hold expired, add-on attached, hotel/room-type/add-on CRUD,
  token revoked, admin ban); `DEBUG` marks write-endpoint entry and non-trivial flow;
  pure reads (`GET` list/detail, pricing) stay quiet. **No full entities or secrets**
  (passwords, raw tokens) are ever logged — only ids and the business-relevant scalar
  fields.
- **Exception logging lives in one place.** `GlobalExceptionHandler` is the sole
  logger of failures, so nothing is logged twice: the unmapped catch-all logs at
  `ERROR` *with the stack trace* (the only place a 500 is recorded); business
  conflicts (409) and security events — failed login (401) and access-denied (403) —
  log at `WARN`; expected client errors (404, state/precondition 400) log at `DEBUG`.
  Every line inherits the request's MDC `requestId`/`traceId`, so a 500 seen by a
  client maps straight to its stack trace. The 401 line never includes the submitted
  email or password.

---

## 4. Request path (booking, end to end)

1. Request hits `JwtAuthenticationFilter` → reads the `jwt` cookie, validates it,
   loads the `Guest`, sets the `SecurityContext`. Missing/invalid → stays anonymous.
2. `SecurityConfig` URL rules admit or reject the route.
3. Controller validates the `@Valid` DTO (fail → 400) and delegates.
4. Service opens a transaction, **locks** the inventory rows for the stay,
   verifies every night has a free count, increments, prices the stay night by
   night, and persists a `PENDING` reservation with an expiry.
5. Payment confirms the hold under the `@Version` guard; the sweeper expires it if
   payment never comes.
6. Result → DTO → status code; any thrown domain exception → `GlobalExceptionHandler`.

---

## 5. HTTP surface (controllers)

| Area              | Access pattern                                              |
|-------------------|-------------------------------------------------------------|
| Auth              | public; issues/clears the `jwt` cookie                      |
| Hotels, RoomTypes, Add-ons | public **read**, admin-only **write**              |
| Search            | availability lookup                                         |
| Reservations      | authenticated; **ownership-scoped** reads/cancel; check-in **admin-only** |
| Payments          | authenticated; ownership-checked                            |
| Maintenance       | admin-only                                                  |

`ReservationController` is the richest contract (mixed gating + ownership +
lifecycle transitions) and is the reference for endpoint-level documentation.

---

## 6. Known technical debt & next steps

An honest ledger of current trade-offs. Security-specific gaps are expanded in
[SECURITY.md §9](SECURITY.md); this is the system-level view.

- **Rate limiter trusts `X-Forwarded-For`.** The per-IP auth throttle reads the
  first `X-Forwarded-For` hop, which a client can spoof unless a trusted proxy that
  rewrites the header sits in front. Acceptable for the current abuse-capping goal;
  a production deployment behind an untrusted network should pin the limiter to the
  socket address or a proxy-verified client IP.
- **`Secure` cookie on local HTTP.** The cookie is always `Secure`, so it won't be
  sent over plain `http://localhost`; local testing needs HTTPS or a profile
  override.
- **SpringDoc / OpenAPI not yet wired.** No `springdoc-openapi` dependency is
  present, and its Boot 4.1 / Spring 7 compatibility is unverified. When added, the
  cookie-auth `SecurityScheme` and per-endpoint `@Operation`/`@ApiResponses`
  annotations should follow the mapping table in §3.
- **Documentation drift.** Treat this document and the tests as the source of
  truth for the contract if a stray source comment ever disagrees.
