-- =============================================================
-- HOTEL BOOKING SYSTEM - DATABASE SCHEMA
-- Normalized to Third Normal Form (3NF)
-- =============================================================


-- -------------------------------------------------------------
-- TABLE: hotel
-- Stores information about each hotel in the system.
-- -------------------------------------------------------------
CREATE TABLE hotel (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    address      VARCHAR(200) NOT NULL,
    city         VARCHAR(50)  NOT NULL,
    country      VARCHAR(100) NOT NULL,
    phone        VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(100) NOT NULL UNIQUE,
    star_rating  INT          NOT NULL,       -- Value between 0 and 5
    timezone     VARCHAR(20),                 -- e.g., 'Africa/Cairo'
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- -------------------------------------------------------------
-- TABLE: room_type
-- Defines a category of room within a hotel (e.g., Deluxe, Suite).
-- A room type belongs to exactly one hotel.
-- The combination of (hotel_id, name) must be unique to prevent
-- duplicate room types within the same hotel.
-- -------------------------------------------------------------
CREATE TABLE room_type (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id             BIGINT          NOT NULL,
    name                 VARCHAR(100)    NOT NULL,
    description          TEXT,
    max_occupancy        INT             NOT NULL,
    total_rooms          INT             NOT NULL,
    base_price_per_night DECIMAL(19, 2)  NOT NULL,
    currency             VARCHAR(3)      NOT NULL DEFAULT 'EGP',
    FOREIGN KEY (hotel_id) REFERENCES hotel(id) ON DELETE CASCADE,
    UNIQUE (hotel_id, name)
);


-- -------------------------------------------------------------
-- TABLE: room_type_inventory
-- The sellable allotment calendar: one row per room type per date.
-- total_rooms is the capacity for that night; booked_count is how many are
-- held by active reservations. A room type is available for a stay only if
-- every night in [check_in, check_out) has a row with booked_count < total_rooms.
-- Booking locks these rows (SELECT ... FOR UPDATE) to prevent double-booking.
-- -------------------------------------------------------------
CREATE TABLE room_type_inventory (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id BIGINT  NOT NULL,
    date         DATE    NOT NULL,
    total_rooms  INT     NOT NULL,
    booked_count INT     NOT NULL DEFAULT 0,
    FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE CASCADE,
    UNIQUE (room_type_id, date)
);


-- -------------------------------------------------------------
-- TABLE: room
-- Represents a single physical room in a hotel.
-- A room belongs to a room_type, and the hotel is derived
-- through room_type (3NF: no transitive redundancy).
-- The combination of (room_type_id, room_number) is unique.
-- -------------------------------------------------------------
CREATE TABLE room (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id BIGINT       NOT NULL,
    room_number  INT          NOT NULL,
    floor        INT          NOT NULL,
    building     VARCHAR(50)  NOT NULL,
    FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE CASCADE,
    UNIQUE (room_type_id, room_number)
);


-- -------------------------------------------------------------
-- TABLE: rate_plan
-- A bookable product/policy for a room type. It carries the guest-facing
-- policies (refundable, breakfast, minimum stay) — but NOT a price or currency.
-- Per-night prices live in rate_plan_rate as sparse date overrides; the
-- currency comes from the room type, keeping a stay single-currency.
-- -------------------------------------------------------------
CREATE TABLE rate_plan (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id        BIGINT          NOT NULL,
    name                VARCHAR(50)     NOT NULL,
    min_stay_nights     INT             DEFAULT 1,
    breakfast_included  BOOLEAN         DEFAULT FALSE,
    is_refundable       BOOLEAN         DEFAULT TRUE,
    FOREIGN KEY (room_type_id) REFERENCES room_type(id) ON DELETE CASCADE
);


-- -------------------------------------------------------------
-- TABLE: rate_plan_rate
-- A price override for a rate plan on a single date. Sparse: only dates whose
-- price differs from the room type's base rate need a row. Any date without a
-- row is billed at room_type.base_price_per_night during pricing.
-- -------------------------------------------------------------
CREATE TABLE rate_plan_rate (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rate_plan_id BIGINT          NOT NULL,
    date         DATE            NOT NULL,
    price        DECIMAL(19, 2)  NOT NULL,
    FOREIGN KEY (rate_plan_id) REFERENCES rate_plan(id) ON DELETE CASCADE,
    UNIQUE (rate_plan_id, date)
);


-- -------------------------------------------------------------
-- TABLE: maintenance_block
-- Records periods where a room is unavailable due to maintenance.
-- -------------------------------------------------------------
CREATE TABLE maintenance_block (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id    BIGINT       NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    reason     VARCHAR(255),
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE
);


-- -------------------------------------------------------------
-- TABLE: guest
-- Stores registered guest profiles.
-- Email is unique as it serves as the login identity.
-- phone is not enforced as UNIQUE because shared family numbers are common.
-- document_type distinguishes between passport, national ID, etc.
-- -------------------------------------------------------------
CREATE TABLE guest (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name      VARCHAR(50)  NOT NULL,
    last_name       VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    phone           VARCHAR(50)  NOT NULL,
    nationality     VARCHAR(2)   NOT NULL,   -- ISO 3166-1 alpha-2 country code (e.g., EG, US)
    document_type   VARCHAR(20)  NOT NULL,   -- e.g., PASSPORT, NATIONAL_ID
    document_number VARCHAR(100) NOT NULL,
    date_of_birth   DATE         NOT NULL,
    loyalty_tier    VARCHAR(20)  DEFAULT 'STANDARD', -- e.g., STANDARD, SILVER, GOLD
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);


-- -------------------------------------------------------------
-- TABLE: reservation
-- Represents a guest's booking for a room type under a specific rate plan.
-- hotel_id is NOT stored here because it can be derived:
--   reservation -> rate_plan -> room_type -> hotel  (3NF compliance)
-- assigned_room_id is nullable: a specific room may not be assigned at booking time.
-- total_price is stored (not computed) to freeze the price at booking time,
--   protecting against future rate changes.
-- -------------------------------------------------------------
CREATE TABLE reservation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    guest_id            BIGINT          NOT NULL,
    rate_plan_id        BIGINT          NOT NULL,
    assigned_room_id    BIGINT          NULL,
    confirmation_number VARCHAR(50)     NOT NULL UNIQUE,
    check_in_date       DATE            NOT NULL,
    check_out_date      DATE            NOT NULL,
    num_guests          INT             NOT NULL,
    total_price         DECIMAL(19, 2)  NOT NULL,  -- Frozen at booking time
    status              VARCHAR(20)     NOT NULL,   -- e.g., CONFIRMED, CANCELLED, CHECKED_IN
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (guest_id)          REFERENCES guest(id)  ON DELETE RESTRICT,
    FOREIGN KEY (rate_plan_id)      REFERENCES rate_plan(id) ON DELETE RESTRICT,
    FOREIGN KEY (assigned_room_id)  REFERENCES room(id)   ON DELETE SET NULL
);


-- -------------------------------------------------------------
-- TABLE: reservation_night
-- The frozen day-by-day price breakdown of a reservation. One row per night in
-- the half-open stay [check_in, check_out). The sum of rate_amount equals
-- reservation.total_price. source records whether the night was priced from a
-- rate plan override (PLAN) or the room type's base rate (BASE).
-- -------------------------------------------------------------
CREATE TABLE reservation_night (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT          NOT NULL,
    date           DATE            NOT NULL,
    rate_amount    DECIMAL(19, 2)  NOT NULL,
    source         VARCHAR(10)     NOT NULL,   -- PLAN | BASE
    FOREIGN KEY (reservation_id) REFERENCES reservation(id) ON DELETE CASCADE,
    UNIQUE (reservation_id, date)
);


-- -------------------------------------------------------------
-- TABLE: payment
-- Records each payment transaction linked to a reservation.
-- A reservation can have multiple payments (e.g., deposit + final payment).
-- transaction_reference: the external ID from the payment gateway (e.g., Stripe, Paymob).
-- -------------------------------------------------------------
CREATE TABLE payment (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id        BIGINT          NOT NULL,
    amount                DECIMAL(19, 2)  NOT NULL,
    currency              VARCHAR(3)      DEFAULT 'EGP',
    type                  VARCHAR(20)     NOT NULL,   -- e.g., DEPOSIT, FINAL_PAYMENT, REFUND
    method                VARCHAR(20)     NOT NULL,   -- e.g., CASH, VISA, MASTERCARD
    provider              VARCHAR(50)     NOT NULL,   -- e.g., STRIPE, PAYMOB, FRONT_DESK
    transaction_reference VARCHAR(100)    UNIQUE,     -- External gateway transaction ID
    status                VARCHAR(20)     NOT NULL,   -- e.g., PENDING, SUCCESS, FAILED, REFUNDED
    created_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservation(id) ON DELETE RESTRICT
);


-- -------------------------------------------------------------
-- TABLE: addon
-- Represents optional services a hotel offers (e.g., airport transfer, spa).
-- Each addon belongs to one hotel so hotels can manage their own pricing.
-- -------------------------------------------------------------
CREATE TABLE addon (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id   BIGINT          NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    category   VARCHAR(50)     NOT NULL,   -- e.g., TRANSPORTATION, FOOD, SPA
    price      DECIMAL(19, 2)  NOT NULL,
    price_unit VARCHAR(20)     NOT NULL,   -- e.g., PER_PERSON, PER_NIGHT, FLAT_RATE
    available  BOOLEAN         DEFAULT TRUE,
    FOREIGN KEY (hotel_id) REFERENCES hotel(id) ON DELETE CASCADE
);


-- -------------------------------------------------------------
-- TABLE: reservation_addon
-- Junction table linking reservations to their selected addons.
-- unit_price is stored here to freeze the addon price at booking time,
--   ensuring historical accuracy even if the addon price changes later.
-- total_price is NOT stored because it is always derivable as
--   (quantity * unit_price), storing it would violate 3NF.
-- Deleting a reservation removes its addon entries (CASCADE).
-- Deleting an addon is blocked if it is referenced by any reservation (RESTRICT).
-- -------------------------------------------------------------
CREATE TABLE reservation_addon (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT          NOT NULL,
    addon_id       BIGINT          NOT NULL,
    quantity       INT             DEFAULT 1,
    unit_price     DECIMAL(19, 2)  NOT NULL,  -- Frozen historical price
    created_at     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservation(id) ON DELETE CASCADE,
    FOREIGN KEY (addon_id)       REFERENCES addon(id)       ON DELETE RESTRICT
);