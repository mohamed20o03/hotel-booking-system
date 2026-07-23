# API Reference

The HTTP contract for the Room Booking API. For the reasoning behind these
contracts see [ARCHITECTURE.md](ARCHITECTURE.md); for the security internals see
[SECURITY.md](SECURITY.md).

- **Base URL:** `http://localhost:8080`
- **Media type:** `application/json` for all request and response bodies.
- **Interactive docs:** SpringDoc/OpenAPI is deferred pending Spring Boot 4 / Spring 7
  compatibility, so this document is the authoritative endpoint reference.

---

## Authentication model

Authentication is a **stateless JWT carried in an `HttpOnly` cookie named `jwt`**, not
a bearer header. `POST /api/auth/register` and `POST /api/auth/login` set the cookie;
the client (browser) then sends it automatically on every subsequent request. There is
nothing to attach manually in a browser; with a CLI client, persist and resend the
cookie.

Two roles exist:
- **`ROLE_USER`** — every self-registered guest.
- **`ROLE_ADMIN`** — hotel staff. **Seeded only, never self-registered.**

### Access scopes used below
- **Public** — no authentication required.
- **Authenticated** — any logged-in guest (`ROLE_USER` or `ROLE_ADMIN`).
- **Owner** — authenticated *and* must own the target resource (enforced in the
  service layer; a mismatch is `400`, see the error contract).
- **Admin** — requires `ROLE_ADMIN`.

---

## Error contract

Every error returns a consistent JSON body, produced centrally by
`GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-07-19T12:34:56.789",
  "status": 404,
  "error": "Not Found",
  "message": "Reservation not found with ID: 42",
  "path": "/api/reservations/42/cancel"
}
```

| Status | When |
|---|---|
| `400 Bad Request` | `@Valid` body validation failed, a malformed date query param (`DateTimeParseException`), or an ownership/state-transition violation raised as `IllegalArgumentException`. |
| `401 Unauthorized` | Unauthenticated access to a protected route (via `RestAuthenticationEntryPoint`), or bad login credentials (`AuthenticationException`). See the note below. |
| `403 Forbidden` | Authenticated but insufficient role (`AccessDeniedException`). See the note below. |
| `404 Not Found` | Target resource does not exist (`ResourceNotFoundException`). |
| `409 Conflict` | Business conflict: no inventory/room (`NoAvailabilityException`), duplicate unique value (`DuplicateResourceException`), or an unpayable reservation (`PaymentException`). |
| `500 Internal Server Error` | Unmapped/unexpected failures. |

> **⚠️ 401 vs 403.** A `RestAuthenticationEntryPoint` is registered, so an
> *unauthenticated* request to a protected route returns **`401 Unauthorized`**,
> while an *authenticated* caller who lacks the required role returns **`403
> Forbidden`**. The two are distinguishable by clients. See
> [ARCHITECTURE.md](ARCHITECTURE.md#error-handling--web-contracts) for the full mapping.

---

## Endpoints

### Authentication — `/api/auth`

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a guest (always `ROLE_USER`); sets the `jwt` cookie. |
| `POST` | `/api/auth/login` | Public | Authenticate an existing guest; sets the `jwt` cookie. |
| `POST` | `/api/auth/logout` | Public | Clears the `jwt` cookie **and** records the token's `jti` in the Redis blacklist (server-side revocation). |
| `PATCH` | `/api/auth/me/password` | Authenticated | Change own password; revokes **all** tokens for the user across every device. |
| `POST` | `/api/auth/admin/ban/{userId}` | Admin | Ban a guest globally; revokes every active token for that user immediately. |

**`POST /api/auth/register`** — body:
```json
{
  "firstName": "Jane",
  "lastName": "Guest",
  "email": "jane@example.com",
  "password": "s3cret-pw",
  "phone": "+201234567890",
  "nationality": "EG",
  "documentType": "PASSPORT",
  "documentNumber": "A1234567",
  "dateOfBirth": "1990-05-01"
}
```
- **`201 Created`** — no body; the `jwt` cookie is set in the `Set-Cookie` response header.
- **`400`** — validation failure. **`409`** — email already registered.

**`POST /api/auth/login`** — body: `{ "email": "jane@example.com", "password": "s3cret-pw" }`
- **`200 OK`** — no body; `jwt` cookie set. **`400`** — validation failure. **`401`** — bad credentials (`AuthenticationException`).

**`POST /api/auth/logout`**
- **`204 No Content`**. The `jwt` cookie is cleared and the token's `jti` is written to the Redis blacklist, so any copy of the token captured before logout is rejected immediately.

**`PATCH /api/auth/me/password`** — body: `{ "currentPassword": "old-pw", "newPassword": "new-pw" }`
- **`204 No Content`**. Verifies the current password, updates it, and writes a per-user revocation entry to Redis — every token for this account across all devices is invalidated at once. The current device's cookie is also cleared.
- **`400`** — validation failure or wrong current password. **`401`** — unauthenticated.

**`POST /api/auth/admin/ban/{userId}`** — body: `{ "reason": "Terms of service violation" }`
- **`200 OK`** with a confirmation message. Writes a per-user revocation entry to Redis, immediately invalidating every active token for the target user.
- **`400`** — blank reason. **`401`** — unauthenticated. **`403`** — not admin. **`404`** — user not found.

---

### Hotels — `/api/hotels`

| Method | Path | Scope | Description |
|---|---|---|---|
| `GET` | `/api/hotels` | Public | List all hotels. |
| `GET` | `/api/hotels/{id}` | Public | Get one hotel by id. |
| `POST` | `/api/hotels` | Admin | Create a hotel. |
| `PUT` | `/api/hotels/{id}` | Admin | Update a hotel. |
| `DELETE` | `/api/hotels/{id}` | Admin | Delete a hotel. |

**`POST /api/hotels`** — body:
```json
{
  "name": "Nile Grand",
  "address": "12 Corniche St",
  "city": "Cairo",
  "country": "EG",
  "phone": "+20222222222",
  "email": "info@nilegrand.example",
  "timezone": "Africa/Cairo"
}
```
- **`201 Created`** → `HotelResponseDTO`. **`400`** validation. **`403`** not admin. **`409`** duplicate phone/email.

---

### Room types — `/api/hotels/{hotelId}/room-types`

| Method | Path | Scope | Description |
|---|---|---|---|
| `GET` | `/api/hotels/{hotelId}/room-types` | Public | List room types for a hotel. |
| `POST` | `/api/hotels/{hotelId}/room-types` | Admin | Create a room type. |
| `PUT` | `/api/hotels/{hotelId}/room-types/{id}` | Admin | Update a room type. |
| `DELETE` | `/api/hotels/{hotelId}/room-types/{id}` | Admin | Delete a room type. |

**Create/update body:**
```json
{
  "name": "Standard Double",
  "description": "Two twin beds, city view",
  "maxOccupancy": 2,
  "totalRooms": 10,
  "basePricePerNight": 100.00,
  "currency": "EGP"
}
```
- **`201 Created`** → `RoomTypeResponseDTO`. **`400`** validation. **`403`** not admin. **`404`** hotel not found.

> `GET /api/hotels/**` is public, so listing room types requires no auth; the mutating
> verbs are admin-only.

---

### Availability search — `/api/hotels/{hotelId}/availability`

| Method | Path | Scope | Description |
|---|---|---|---|
| `GET` | `/api/hotels/{hotelId}/availability` | Public | List bookable room types with remaining counts for a stay. |

**Query params:** `checkIn` (ISO date), `checkOut` (ISO date), `guests` (int).
Example: `/api/hotels/1/availability?checkIn=2026-12-10&checkOut=2026-12-13&guests=2`
- **`200 OK`** → array of `AvailabilityResponseDTO` (`roomTypeId`, `roomTypeName`,
  `description`, `maxOccupancy`, `availableRoomsCount`, `basePricePerNight`, `currency`).
- **`400`** — a non-ISO `checkIn`/`checkOut` raises `DateTimeParseException`, mapped to Bad Request.

---

### Reservations — `/api/reservations`

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/reservations` | Authenticated | Create a booking; holds inventory, returns a `PENDING` confirmation. |
| `GET` | `/api/reservations/{confirmationNumber}` | Authenticated | Look up a reservation by its confirmation code. |
| `GET` | `/api/reservations/my-reservations` | Authenticated | List the caller's own reservations (ownership-scoped by token). |
| `PATCH` | `/api/reservations/{id}/cancel` | Owner | Cancel the caller's own `PENDING`/`CONFIRMED` booking; releases inventory. |
| `PATCH` | `/api/reservations/{id}/check-in` | Admin | Assign a physical room and move `CONFIRMED` → `CHECKED_IN`. |

**`POST /api/reservations`** — body:
```json
{
  "ratePlanId": 1,
  "checkInDate": "2026-12-10",
  "checkOutDate": "2026-12-13",
  "numGuests": 2
}
```
- **`201 Created`** → `ReservationConfirmationDTO` (status `PENDING`). Dates must be
  present/future and check-out after check-in.
- **`400`** validation / invalid date range. **`401`** unauthenticated. **`409`** no inventory for a night in the range.

**`PATCH /api/reservations/{id}/cancel`**
- **`204 No Content`** on success. **`400`** caller does not own it, or status not
  cancellable. **`404`** id not found.

**`PATCH /api/reservations/{id}/check-in`**
- **`200 OK`** → `ReservationResponseDTO` with the assigned room. **`400`** not
  `CONFIRMED`. **`403`** not admin. **`404`** id not found. **`409`** no physical room free to assign.

---

### Reservation add-ons — `/api/reservations/{id}/addons`

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/reservations/{id}/addons` | Owner | Attach a catalogue add-on to the caller's own reservation; bumps the total. |
| `GET` | `/api/reservations/{id}/addons` | Owner | List add-ons on the caller's reservation. |
| `DELETE` | `/api/reservations/{id}/addons/{addonLineId}` | Owner | Remove an attached add-on line. |

**Attach body:** `{ "addonId": 5, "quantity": 2 }`
- **`201 Created`** → `ReservationAddonResponseDTO` (unit price frozen server-side).
  **`400`** ownership/state violation. **`404`** reservation or add-on not found.

---

### Hotel add-on catalogue — `/api/hotels/{hotelId}/addons`

| Method | Path | Scope | Description |
|---|---|---|---|
| `GET` | `/api/hotels/{hotelId}/addons` | Public | List a hotel's add-on catalogue. |
| `POST` | `/api/hotels/{hotelId}/addons` | Admin | Add a catalogue item. |
| `PUT` | `/api/hotels/{hotelId}/addons/{addonId}` | Admin | Update a catalogue item. |
| `DELETE` | `/api/hotels/{hotelId}/addons/{addonId}` | Admin | Remove a catalogue item. |

- Mutations: **`201/200`** on success, **`403`** not admin, **`404`** hotel/add-on not found.

---

### Payments — `/api/payments`

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/payments` | Authenticated (Owner) | Pay for a reservation; confirms the hold (`PENDING` → `CONFIRMED`). |

**Body:** `{ "reservationId": 1, "amount": 300.00, "method": "CARD" }`
- **`200 OK`** → `PaymentResponseDTO` (includes `status` and the resulting
  `reservationStatus`).
- **`400`** validation, or caller does not own the reservation. **`404`** reservation
  not found. **`409`** reservation not in a payable state (`PaymentException`).

---

### Guests — `/api/guests`

| Method | Path | Scope | Description |
|---|---|---|---|
| `GET` | `/api/guests/{id}` | Authenticated | Fetch a guest profile by id. |

- **`200 OK`** → `GuestResponseDTO`. **`401`** unauthenticated. **`404`** not found.

---

### Maintenance blocks — `/api/maintenance`

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/maintenance` | Admin | Block a physical room for a date range. |
| `DELETE` | `/api/maintenance/{id}` | Admin | Remove a maintenance block. |

**Body:** `{ "roomId": 101, "startDate": "2026-12-01", "endDate": "2026-12-05", "reason": "Repainting" }`
(the range is half-open — `endDate` exclusive).
- **`201 Created`** → `MaintenanceBlockResponseDTO`. **`400`** validation / invalid
  range. **`403`** not admin. **`404`** room not found.

---

## Quick start (cURL)

```bash
# 1. Register (stores the jwt cookie in cookies.txt)
curl -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Guest","email":"jane@example.com","password":"s3cret-pw","phone":"+201234567890","nationality":"EG","documentType":"PASSPORT","documentNumber":"A1234567","dateOfBirth":"1990-05-01"}'

# 2. Search availability (public)
curl 'http://localhost:8080/api/hotels/1/availability?checkIn=2026-12-10&checkOut=2026-12-13&guests=2'

# 3. Book a stay (sends the cookie back)
curl -b cookies.txt -X POST http://localhost:8080/api/reservations \
  -H 'Content-Type: application/json' \
  -d '{"ratePlanId":1,"checkInDate":"2026-12-10","checkOutDate":"2026-12-13","numGuests":2}'

# 4. Pay to confirm
curl -b cookies.txt -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"reservationId":1,"amount":300.00,"method":"CARD"}'
```

> Because the cookie is `Secure`, these calls work against an HTTPS deployment; over
> plain `http://localhost` the browser withholds the cookie (CLI clients using
> `-b/-c` files are unaffected).
