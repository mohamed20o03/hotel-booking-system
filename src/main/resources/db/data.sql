INSERT INTO users (first_name, middle_name, last_name, birth_date, gender) VALUES
('Mohamed', 'Ahmed', 'Abdo', '1998-05-15', 'MALE'),
('Sarah', NULL, 'Connor', '1992-08-22', 'FEMALE'),
('Alex', 'James', 'Smith', '1985-11-30', 'OTHER');

INSERT INTO rooms (floor, building) VALUES
('1', '10'),
('2', '10'),
('1', '15');

INSERT INTO bookings (user_id, room_id, start_date, end_date) VALUES
(1, 1, '2026-06-20 09:00:00', '2026-06-20 11:00:00'),
(2, 2, '2026-06-20 13:00:00', '2026-06-20 15:00:00'),
(3, 1, '2026-06-20 12:00:00', '2026-06-20 14:00:00');
