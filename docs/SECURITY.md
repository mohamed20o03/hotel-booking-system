# Security — Deep Dive

> **Audience:** engineers working on authentication, authorization, or anything that
> touches the security filter chain.
> **Scope:** a complete, code-level account of how auth works in Hotel Booking System — the
> mechanisms, the exact code paths, the threats each choice addresses, and the places
> where the design has sharp edges.
> **Companion docs:** [ARCHITECTURE.md](ARCHITECTURE.md) (system overview) ·
> [API.md](API.md) (HTTP contract).

Two decisions define the whole model. Everything else follows from them:

1. **The JWT rides in an `HttpOnly` cookie, not the `Authorization` header.**
2. **Roles are read from the database on every request, never trusted from the token.**

Hold those two in mind — most of what looks unusual below is a consequence of one
or the other.

---

## At a glance

| Question | Short answer | Detail |
|---|---|---|
| How is a caller identified? | Signed JWT in an `HttpOnly` cookie named `jwt` | [§2](#2-the-token-byte-by-byte), [§5](#5-the-cookie-attribute-by-attribute) |
| Where do roles come from? | The `Guest.role` DB column, reloaded every request | [§6](#6-authorization-roles-and-ownership) |
| What validates the token? | `JwtService` (HMAC-SHA256 signature + expiry) | [§2](#2-the-token-byte-by-byte) |
| What enforces access? | Three layers: URL rules → `@PreAuthorize` → service-layer ownership | [§6](#6-authorization-roles-and-ownership) |
| How are passwords stored? | BCrypt (adaptive, salted) — hash only | [§7](#7-password-handling) |
| Is the session stateful? | No — `STATELESS`; the token is the whole session | [§6](#6-authorization-roles-and-ownership) |
| Can I log someone out server-side? | **Yes** — global ban via Redis invalidates all tokens instantly | [§3](#3-registration-and-login), [§8](#8-threat-model) |

---

## Table of contents

Sections are grouped into four parts. Section numbers are stable — the body cross-references them (e.g. "see §6").

**Part I · Foundations**
1. [Component map](#1-component-map) — the pieces and how they wire together at runtime.
2. [The token, byte by byte](#2-the-token-byte-by-byte) — JWT structure, signing, verifying, validation.

**Part II · The authentication flow**
3. [Registration and login](#3-registration-and-login) — how a caller obtains a token.
4. [The per-request filter, line by line](#4-the-per-request-filter-line-by-line) — how every request is authenticated.
5. [The cookie, attribute by attribute](#5-the-cookie-attribute-by-attribute) — the transport and its flags.

**Part III · Authorization**
6. [Authorization: roles and ownership](#6-authorization-roles-and-ownership) — the three enforcement layers.
7. [Password handling](#7-password-handling) — hashing and verification.

**Part IV · Hardening & operations**
8. [Threat model](#8-threat-model) — what the design stops, and how.
9. [Known gaps and how to close them](#9-known-gaps-and-how-to-close-them) — the honest ledger.
10. [Change-this-here reference](#10-change-this-here-reference) — where to edit each knob.

---

# Part I · Foundations

## 1. Component map

| Component | File | Responsibility |
|---|---|---|
| `SecurityConfig` | `security/SecurityConfig.java` | Builds the `SecurityFilterChain`; declares URL-level rules; inserts the JWT filter; disables CSRF; sets stateless sessions |
| `JwtAuthenticationFilter` | `security/JwtAuthenticationFilter.java` | Once per request: reads cookie → validates token → checks blacklist → loads `Guest` → populates `SecurityContext` |
| `JwtService` | `security/JwtService.java` | Signs, parses, and validates tokens; owns the HMAC key; generates compound `jti` |
| `TokenBlacklistService` | `security/TokenBlacklistService.java` | Redis-backed revocation store; supports token-level and user-level (global) bans |
| `ApplicationConfig` | `security/ApplicationConfig.java` | Beans: `UserDetailsService`, `AuthenticationProvider`, `PasswordEncoder`, `AuthenticationManager` |
| `AuthenticationService` | `service/AuthenticationService.java` | Orchestrates register/login/global-revocation; delegates to `JwtService` and `TokenBlacklistService` |
| `GuestService` | `service/GuestService.java` | Guest account management including password change with BCrypt re-verification |
| `AuthController` | `controller/AuthController.java` | `/api/auth/*` endpoints: register, login, logout, admin ban, and self-service password change |
| `Guest` | `model/Guest.java` | Implements `UserDetails`; its `role` column is the sole authority source |
| `GlobalExceptionHandler` | `exception/GlobalExceptionHandler.java` | Maps `AuthenticationException` → 401, `AccessDeniedException` → 403, `BadCredentialsException` → 401 |
| `RestAuthenticationEntryPoint` | `security/RestAuthenticationEntryPoint.java` | Answers unauthenticated access to a protected route with 401 |

How they connect at runtime:

```
              request
                 │
                 ▼
   ┌─────────────────────────────┐
   │  JwtAuthenticationFilter     │  ── asks ──▶  JwtService  (validate signature + expiry)
   │  (OncePerRequestFilter)      │  ── asks ──▶  UserDetailsService ──▶ GuestRepository (load Guest)
   └─────────────────────────────┘
                 │ sets Authentication (with Guest.getAuthorities())
                 ▼
   ┌─────────────────────────────┐
   │  SecurityFilterChain rules   │  ── URL-level authorize checks (SecurityConfig)
   └─────────────────────────────┘
                 │
                 ▼
   ┌─────────────────────────────┐
   │  Controller method           │  ── @PreAuthorize("hasRole('ADMIN')") (method-level)
   │                              │  ── service-layer ownership checks
   └─────────────────────────────┘
```

Note the two independent lookups per authenticated request: `JwtService` verifies
the token cryptographically, and `UserDetailsService` loads the live `Guest` row.
They answer different questions — *"is this token genuine and unexpired?"* versus
*"who is this and what may they currently do?"*

---

## 2. The token, byte by byte

A JWT is three Base64url segments joined by dots: `header.payload.signature`.

**Header** — algorithm and type:
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload** — the claims this app sets (`JwtService.generateToken`):
```json
{
  "sub": "guest@example.com",   // subject = the guest's email, the identity
  "iat": 1752900000,            // issued-at (epoch seconds)
  "exp": 1752986400             // expiry = iat + 86400000ms (1 day)
}
```
Note what is **absent**: there is no `role` claim, no `guestId`, no authorities.
That omission is deliberate and is the reason a stolen-but-unexpired token can
never escalate privileges — see §6.

**Signature** — `HMACSHA256( base64url(header) + "." + base64url(payload), secret )`.

### Signing (`generateToken`)

```java
Jwts.builder()
    .setClaims(extraClaims)                 // empty map in practice
    .setSubject(guest.getEmail())
    .setIssuedAt(new Date(...))
    .setExpiration(new Date(now + jwtExpiration))
    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
    .compact();
```

`getSigningKey()` Base64-decodes `spring.application.security.jwt.secret-key` into
raw bytes and wraps them with `Keys.hmacShaKeyFor(...)`. HS256 is **symmetric** —
the same secret both signs (here) and verifies (below). There is no public/private
split; anyone holding the secret can mint valid tokens. That is why the secret is
the crown jewel (§9).

### Verifying (`extractAllClaims`)

Every read of a claim funnels through one parse:

```java
Jwts.parserBuilder()
    .setSigningKey(getSigningKey())
    .build()
    .parseClaimsJws(token)   // ← verification happens HERE
    .getBody();
```

`parseClaimsJws` does four things in order, and throws on any failure:
1. splits and Base64url-decodes the token,
2. recomputes the HMAC over `header.payload` with the secret,
3. compares it to the signature segment — mismatch → `SignatureException`,
4. checks `exp` — past → `ExpiredJwtException`.

Because this throws *before* `getBody()` returns, **claims are never trusted until
the signature is proven.** A tampered payload can't get as far as being read.

### Validation (`isTokenValid`)

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    String userEmail = extractUsername(token);
    return userEmail.equals(userDetails.getUsername()) && !isTokenExpired(token);
}
```

This is a second, belt-and-suspenders check on top of the parse: it confirms the
`sub` matches the `Guest` that was loaded from the DB. In the normal flow the email
*is* what we used to load that guest, so this is somewhat redundant — but it means
the token and the resolved principal can never disagree.

---

# Part II · The authentication flow

## 3. Registration, login, and credential flows

All token-issuing endpoints end the same way: a signed token delivered in a cookie,
never in the response body. Revocation endpoints clear the cookie and write to Redis.

```
POST /api/auth/register                 POST /api/auth/login
        │                                       │
        ▼                                       ▼
AuthenticationService.register          AuthenticationService.login
        │                                       │
GuestService.registerGuest              authenticationManager.authenticate(
  • BCrypt-hash the password              new UsernamePasswordAuthenticationToken(
  • role = "ROLE_USER" (hard-wired)          email, rawPassword))
        │                                    │  → DaoAuthenticationProvider:
        │                                    │      load Guest, BCrypt-compare
        │                                    │      throws if credentials bad
        ▼                                       ▼
reload Guest by email                    reload Guest by email
        └──────────────────┬────────────────────┘
                           ▼
              jwtService.generateTokens(guest)
                           ▼
   AuthController: response.addHeader(SET_COOKIE, buildJwtCookie(token))
                           ▼
        201 Created (register)  /  200 OK (login)   — empty body
```

Key points, each grounded in the code:

- **Registration auto-logs-in.** `register()` issues a token immediately, so the
  client is authenticated the instant it signs up — no separate login round-trip.
- **Login never hashes anything itself.** It hands the raw email+password to
  `AuthenticationManager.authenticate(...)`, which delegates to
  `DaoAuthenticationProvider`, which uses the `BCryptPasswordEncoder` bean to
  compare against the stored hash. If the password is wrong, `authenticate` throws
  and no token is minted.
- **Both re-fetch the `Guest` after authenticating** to hand a full entity to
  `generateTokens`. (Only the email actually ends up in the token.)

### Admin ban — `POST /api/auth/admin/ban/{userId}`

Restricted to `ROLE_ADMIN` at both the URL-matcher and `@PreAuthorize` levels.
Writes a single Redis key `blacklist:user:<userId>` with a TTL equal to the maximum
JWT lifespan (24 h). Every subsequent request bearing a token whose compound `jti`
starts with that user ID is rejected by `JwtAuthenticationFilter` before it reaches
any controller — across every device, with a single Redis write.

```
POST /api/auth/admin/ban/{userId}   body: { userId, reason }
        │
        ▼  @PreAuthorize("hasRole('ADMIN')")
AuthenticationService.logoutFromAllDevices(userId, reason)
        │
        ▼
TokenBlacklistService.banUserGlobally(userId, 86400, reason)
  → Redis SET blacklist:user:<userId> <reason> EX 86400
        │
        ▼
200 OK  "User <id> has been banned. Reason: <reason>"
```

### Guest password change — `PATCH /api/auth/me/password`

Available to any authenticated guest. Two-step security ensures a stolen cookie alone
cannot silently take over an account:

1. **Re-authentication.** `GuestService.changePassword` verifies `currentPassword`
   against the stored BCrypt hash. A mismatch throws `BadCredentialsException` (→ 401)
   before any write.
2. **Global revocation.** On success, `AuthenticationService.logoutFromAllDevices`
   bans all pre-existing tokens (`PASSWORD_CHANGED`), and the current device's cookie
   is cleared. Every other logged-in device is immediately forced to re-authenticate.

```
PATCH /api/auth/me/password   body: { currentPassword, newPassword }
        │
        ▼  (authenticated via cookie)
GuestService.changePassword(email, request)
  • BCrypt.matches(currentPassword, storedHash) — throws 401 on mismatch
  • guest.setPassword(BCrypt.encode(newPassword))
  • guestRepository.save(guest)  → returns guestId
        │
        ▼
AuthenticationService.logoutFromAllDevices(guestId, "PASSWORD_CHANGED")
  → Redis SET blacklist:user:<guestId> PASSWORD_CHANGED EX 86400
        │
        ▼
Set-Cookie: jwt=""; Max-Age=0   (clears current device's cookie)
204 No Content
```

### The `UserDetailsService` bridge

```java
@Bean
public UserDetailsService userDetailsService() {
    return username -> guestRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
}
```

`Guest implements UserDetails`, so the entity loaded from the DB *is* the security
principal directly — no adapter/wrapper. This is what makes "roles from the DB"
(§6) trivial: `getAuthorities()` is a method on the entity itself.

---

## 4. The per-request filter, line by line

`JwtAuthenticationFilter extends OncePerRequestFilter`, so it runs exactly once per
request regardless of internal forwards. It is inserted **before**
`UsernamePasswordAuthenticationFilter` in `SecurityConfig`:

```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

The body, in six steps:

**Step 1 — find the cookie.** If there are no cookies at all, or none named `jwt`,
the filter calls `filterChain.doFilter(...)` and returns *immediately*. Crucially,
it does **not** reject the request here:

```java
if (request.getCookies() == null) { filterChain.doFilter(request, response); return; }
jwt = Arrays.stream(request.getCookies())
        .filter(c -> "jwt".equals(c.getName()))
        .map(Cookie::getValue).findFirst().orElse(null);
if (jwt == null) { filterChain.doFilter(request, response); return; }
```

The filter's only job is *"authenticate if possible."* Deciding whether an
unauthenticated request is *allowed* belongs to the authorization rules downstream.
This separation is why public endpoints (browse hotels, login) work with no cookie.

**Step 2 — read the subject.** `jwtService.extractUsername(jwt)` pulls `sub`. This
step alone triggers a full signature+expiry verification inside `extractAllClaims`;
a bad token throws here.

**Step 3 — guard against double-auth.**
```java
if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
```
If something earlier in the chain already authenticated the request, we don't
clobber it.

**Step 4 — load the live user.** `userDetailsService.loadUserByUsername(userEmail)`
hits the database. This is the moment the design earns its "roles from DB" property:
a deleted user fails here; a role change is picked up here.

**Step 5 — validate, then populate the context.**
```java
if (jwtService.isTokenValid(jwt, userDetails)) {
    var authToken = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authToken);
}
```
The authorities come from `userDetails.getAuthorities()` — i.e. `Guest.getAuthorities()`
— **not** from the token. The principal stored is the `Guest` entity, so controllers
can reach it via `@AuthenticationPrincipal` and services via `SecurityContextHolder`.

**Step 6 — continue.** `filterChain.doFilter(...)` runs whether or not
authentication succeeded. If the endpoint required auth and none was set,
`RestAuthenticationEntryPoint` produces a **401** downstream; an authenticated
caller lacking the required role gets a **403**.

> **Subtle point:** validation failures inside step 5 are swallowed silently — the
> filter simply leaves the context empty and continues. The request then gets
> rejected by the authorization rules, not by an explicit "bad token" error. That
> keeps the filter simple but means the client sees a generic **401** (via the entry
> point), not "your token expired." See §9.

---

## 5. The cookie, attribute by attribute

Built in `AuthController.buildJwtCookie`:

```java
ResponseCookie.from("jwt", token)
    .httpOnly(true)
    .secure(true)
    .path("/")
    .maxAge(86400)
    .sameSite("Strict")
    .build();
```

| Attribute | Value | Precise effect | Threat closed |
|---|---|---|---|
| `HttpOnly` | `true` | The cookie is invisible to `document.cookie` and all JS. | **XSS token theft.** Injected script cannot read or exfiltrate the token. A token in `localStorage` (the header approach) *can* be read by any script on the page. |
| `Secure` | `true` | Browser sends the cookie only over HTTPS. | **Network interception / MITM** on plaintext HTTP. |
| `SameSite` | `Strict` | Browser omits the cookie on *any* cross-site request. | **CSRF.** A malicious site can't cause the browser to attach the session cookie to a forged request. |
| `Path` | `/` | Sent to every path under the domain. | — (scope, not defense) |
| `Max-Age` | `86400` | Cookie self-deletes after 1 day, matching `exp`. | Limits the window a leaked cookie is useful; keeps cookie and token lifetimes in sync. |

**Logout** (`buildClearCookie`) re-sends the same name/path with `maxAge(0)` and an
empty value, which the browser interprets as "delete." The server-side blacklist step
(recording the token's `jti` in Redis) runs first, so a copy of the token captured
before logout is also rejected server-side on its next use.

---

# Part III · Authorization

## 6. Authorization: roles and ownership

Two roles: `ROLE_USER` (every self-registered guest) and `ROLE_ADMIN` (staff).

### The defining property: roles live in the database

```java
// Guest.java
@Column(nullable = false, length = 20)
@Builder.Default
private String role = "ROLE_USER";

@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(role));
}
```

Because `JwtAuthenticationFilter` reloads the `Guest` on **every** request (§4,
step 4) and reads authorities from the entity, three properties fall out for free:

1. **A token cannot claim a role.** There's no `role` claim to forge; authorities
   are re-derived from the DB each time. A stolen token grants exactly the
   privileges its owner *currently* has — no more.
2. **Role changes are immediate.** Promote a guest to `ROLE_ADMIN` in the DB and
   their *next* request is an admin request — no re-login, no token refresh.
3. **Revocation of privileges is immediate** for the same reason (the token itself
   still can't be revoked — that's the gap in §9).

The `role` column is stored *with* its `ROLE_` prefix (`ROLE_ADMIN`), which is what
`getAuthorities()` needs. `hasRole("ADMIN")` auto-prepends `ROLE_`, so the two line
up — keep that convention if you add roles.

### Layer 1 — URL rules (`SecurityConfig`)

```java
// Public: registration, login, and logout only.
.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
// Admin ban — requires ROLE_ADMIN (also guarded by @PreAuthorize).
.requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
// Password change — requires any authenticated session.
.requestMatchers("/api/auth/me/**").authenticated()
.requestMatchers(HttpMethod.GET, "/api/hotels/**").permitAll()
.requestMatchers(HttpMethod.POST,   "/api/hotels/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.PUT,    "/api/hotels/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/hotels/**").hasRole("ADMIN")
.requestMatchers("/api/maintenance/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.PATCH, "/api/reservations/*/check-in").hasRole("ADMIN")
.anyRequest().authenticated()
```

Order matters: the specific `GET /api/hotels/**` public rule sits above the
verb-specific admin rules, so **reads are public but writes are admin** on the same
path prefix. Add-ons and room-types are nested under `/api/hotels/**`, so they
inherit this public-read / admin-write split automatically — no extra rules needed.

`anyRequest().authenticated()` is the catch-all: booking, paying, cancelling, and
"my reservations" require a valid session but no particular role.

Also set here: `csrf.disable()` (safe *only because* of `SameSite=Strict`, see §8)
and `SessionCreationPolicy.STATELESS` (no `HttpSession`; the token is the entire
session — nothing is stored server-side).

### Layer 2 — method annotations

`@EnableMethodSecurity` (on `SecurityConfig`) turns on `@PreAuthorize`. The admin
controller methods carry:

```java
@PreAuthorize("hasRole('ADMIN')")
```

on maintenance create/remove, hotel/room-type/add-on writes, reservation check-in,
**and the new admin ban endpoint** (`POST /api/auth/admin/ban/{userId}`). This
**repeats** the URL-level guard on purpose: if someone later refactors the URL
matchers and drops a rule, the method annotation still refuses non-admins.
Defense in depth — two independent gates, either alone sufficient.

### Layer 3 — ownership (service layer)

Roles answer "may this *kind* of user do this?" They do **not** answer "may this
user touch *this specific* record?" Two `ROLE_USER`s are both authorized to cancel
"a reservation" — but only their own. That check lives in the services:

```java
String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
if (!reservation.getGuest().getEmail().equals(currentEmail)) {
    throw new IllegalArgumentException("You do not have permission to ...");
}
```

Present on: cancel reservation, pay, and attach/detach add-ons. Without it, RBAC
alone would let any logged-in guest operate on any booking by guessing an ID — a
classic IDOR. Ownership is the fix.

### How the first admin exists

Self-registration is hard-wired:

```java
// GuestService.registerGuest
.role("ROLE_USER")   // self-registration never mints an admin
```

No request body field, no endpoint, can produce an admin. The single admin is
**seeded in `db/data.sql`** with `role = 'ROLE_ADMIN'`. Any further promotion is a
deliberate database operation. This closes the "sign up as admin" hole entirely.

---

## 7. Password handling

- **Hashing:** `BCryptPasswordEncoder` (`ApplicationConfig`). BCrypt is adaptive and
  salted per-hash, so identical passwords produce different stored values and brute
  force is deliberately slow.
- **At rest:** only the hash is stored (`guest.password`); the raw password exists
  only for the duration of the register/login request and is never logged.
- **Verification:** never done by hand — `DaoAuthenticationProvider` calls
  `encoder.matches(raw, storedHash)` during `authenticate(...)`.
- **The seeded admin** ships as a known hash of `admin123` in `data.sql`. Fine for
  local dev; a hard no for anything real (§9).

---

# Part IV · Hardening & operations

## 8. Threat model

What the current design stops, and how:

| Attack | Stopped by | Mechanism |
|---|---|---|
| **Token forgery / tampering** | HS256 signature | `parseClaimsJws` recomputes the HMAC and throws before claims are read (§2). |
| **XSS stealing the token** | `HttpOnly` cookie | JS has no API to read the cookie (§5). |
| **CSRF** | `SameSite=Strict` | Browser never attaches the cookie to cross-site requests, so `csrf.disable()` is safe (§5, §6). |
| **MITM on the wire** | `Secure` cookie + HTTPS | Cookie only travels over TLS (§5). |
| **Privilege escalation via token** | Roles from DB, not token | No role claim to forge; authorities re-derived each request (§6). |
| **Sign-up-as-admin** | Hard-wired `ROLE_USER` | No code path sets admin except the DB seed (§6). |
| **IDOR (acting on others' bookings)** | Ownership checks | Email-vs-owner comparison in services (§6). |
| **Brute-forcing stored passwords** | BCrypt | Adaptive, salted, slow (§7). |
| **Stale privileges after demotion** | DB reload per request | Authorities reflect the current row, not the token (§6). |
| **Multi-device session after ban** | Global user-level revocation | Admin ban writes `blacklist:user:<id>` in Redis; filter rejects all existing tokens for that user (§3). |
| **New login after ban** | `Guest.banned` DB flag | `isAccountNonLocked()` returns `false`; `DaoAuthenticationProvider` throws `LockedException` (→ 401) before password check (§6). |
| **Cookie-only password takeover** | BCrypt re-verification on change | `changePassword` verifies current password before writing; stolen cookie alone cannot update credentials (§3). |
| **Stale sessions after password change** | Global revocation on password change | All pre-existing tokens are banned (`PASSWORD_CHANGED`) and the current device's cookie is cleared (§3). |

The interlock worth internalizing: **cookie transport trades the XSS surface for a
CSRF surface, and `SameSite=Strict` pays off that CSRF debt.** Disabling Spring's
CSRF tokens is only defensible *because* of that attribute. If you ever loosen
`SameSite` (e.g. to `Lax` or `None` for cross-site flows), you must re-enable CSRF
protection or you reopen the hole.

---

## 9. Known gaps and how to close them

Honest list — none are hidden, and several are the project's natural next steps.

1. **Seeded admin credential in the repo.** `data.sql` carries a BCrypt hash of
   `admin123`. **Fix:** seed only in a dev profile, or create the admin out-of-band.

2. **`Secure=true` in local dev.** The cookie only flies over HTTPS, so plain-HTTP
   local testing won't receive it. **Fix:** profile-conditional `secure` flag.

3. **HTTP-layer test coverage is partial.** `MaintenanceControllerTest` is the
   reference proving RBAC/validation/error-mapping over the wire, but most
   controllers are still only covered at the service level. **Fix:** extend the
   reference pattern to the other role-gated and validation-heavy endpoints,
   including the new ban and password-change endpoints.

4. **`login()` uses a bare `.orElseThrow()`** with no message when re-fetching the
   guest. Harmless (auth already succeeded) but yields an opaque 500 if it ever
   fires. **Fix:** throw a descriptive exception.
---

## 10. Change-this-here reference

| To change… | Edit |
|---|---|
| Token lifetime | `spring.application.security.jwt.expiration` in `application.yaml` |
| Signing secret | `spring.application.security.jwt.secret-key` (→ move to env var) |
| Which URLs need which role | the `authorizeHttpRequests` block in `SecurityConfig` |
| A single endpoint's role guard | `@PreAuthorize` on the controller method |
| Cookie flags (HttpOnly/Secure/SameSite/Max-Age) | `buildJwtCookie` in `AuthController` |
| Password hashing algorithm | the `PasswordEncoder` bean in `ApplicationConfig` |
| How authorities are derived | `Guest.getAuthorities()` |
| Add a new role | set it on `Guest.role`; guard with `hasRole('NAME')` (no `ROLE_` prefix in the check) |
| Response status for access-denied | `GlobalExceptionHandler` (`AccessDeniedException` → 403) |
| Response status for unauthenticated | `RestAuthenticationEntryPoint` (→ 401) |
| Ban TTL (how long the global ban key lives) | `MAX_TOKEN_LIFESPAN_SECONDS` in `AuthenticationService` |
| Minimum password length on change | `@Size(min = …)` on `ChangePasswordRequestDTO.newPassword` |
| Redis host/port for token blacklist | `spring.data.redis.*` in `application-dev.yaml` / `application-prod.yaml` |
| Lift a ban (re-enable a guest) | Set `guest.banned = false` directly in the DB (no endpoint yet) |

---

