-- =============================================================
-- SEED DATA
-- Demonstrates the inventory-vs-pricing split: a room type with a
-- year-round base rate, plus a rate plan whose sparse overrides make
-- a "Summer Promo" cheaper for Aug 5–15 only. A stay spanning those
-- dates is priced day-by-day (base fallback + overrides).
-- Guests are created at runtime via POST /api/auth/register.
-- =============================================================

-- Hotel
INSERT INTO hotel (name, address, city, country, phone, email, star_rating, timezone) VALUES
('Nile Grand Hotel', '1 Corniche El Nil', 'Cairo', 'Egypt', '+20 2 1234 5678', 'info@nilegrand.example', 5, 'Africa/Cairo');

-- Room types (base rate + currency). Base = 100 EGP/night.
INSERT INTO room_type (hotel_id, name, description, max_occupancy, total_rooms, base_price_per_night, currency) VALUES
(1, 'Standard Double', 'Comfortable double room with city view.', 2, 3, 100.00, 'EGP'),
(1, 'Deluxe Suite',    'Spacious suite with Nile view and lounge.', 4, 2, 250.00, 'EGP');

-- Physical rooms
INSERT INTO room (room_type_id, room_number, floor, building) VALUES
(1, 101, 1, 'Main'),
(1, 102, 1, 'Main'),
(1, 103, 1, 'Main'),
(2, 201, 2, 'Main'),
(2, 202, 2, 'Main');

-- Rate plans (policy only — no price here)
INSERT INTO rate_plan (room_type_id, name, currency, min_stay_nights, breakfast_included, is_refundable) VALUES
(1, 'Flexible',       'EGP', 1, TRUE,  TRUE),   -- id 1: refundable, breakfast included
(1, 'Non-Refundable', 'EGP', 2, FALSE, FALSE),  -- id 2: cheaper promo, stricter policy
(2, 'Flexible',       'EGP', 1, TRUE,  TRUE);   -- id 3: for the Deluxe Suite

-- Summer Promo overrides for the Non-Refundable plan (id 2): Aug 5–15 2026 at 80 EGP.
-- Any date NOT listed here falls back to the room type base rate (100 EGP).
INSERT INTO rate_plan_rate (rate_plan_id, date, price) VALUES
(2, '2026-08-05', 80.00),
(2, '2026-08-06', 80.00),
(2, '2026-08-07', 80.00),
(2, '2026-08-08', 80.00),
(2, '2026-08-09', 80.00),
(2, '2026-08-10', 80.00),
(2, '2026-08-11', 80.00),
(2, '2026-08-12', 80.00),
(2, '2026-08-13', 80.00),
(2, '2026-08-14', 80.00),
(2, '2026-08-15', 80.00);
