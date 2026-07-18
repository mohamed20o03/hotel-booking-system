package com.Abdelwahab.RoomBooking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;

/**
 * Repository test for RoomRepository.findAvailableRooms — the non-trivial JPQL
 * with a double NOT EXISTS (overlapping reservations + maintenance blocks).
 * Only a real database proves this query behaves; Mockito cannot.
 *
 * Why @AutoConfigureTestDatabase(replace = NONE):
 *   The app uses ddl-auto=validate and creates its schema from schema.sql. By
 *   default @DataJpaTest swaps in its own embedded DB and lets Hibernate build
 *   the schema — which would bypass schema.sql and clash with 'validate'. NONE
 *   keeps the configured H2 + schema.sql wiring, so the test runs against the
 *   exact same schema as production.
 *
 * Note: data.sql seed rows exist in the DB, but every query here is scoped to a
 * room type this test creates, so seeded rows never affect the assertions.
 * @DataJpaTest wraps each test in a transaction and rolls it back afterward.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
// Each context gets its own in-memory DB via the global ${random.uuid} datasource
// URL in src/test/resources/application.properties, so schema.sql runs cleanly once
// per context. Without that isolation, a second context reusing the same DB would
// fail with "table already exists".
public class RoomRepositoryTest {

    @Autowired private RoomRepository roomRepository;
    @Autowired private TestEntityManager em;

    // The stay under test: Jun 10 -> Jun 13 (nights of the 10th, 11th, 12th).
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 6, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 6, 13);

    private RoomType roomType;
    private int seq; // keeps unique-constrained fixture fields distinct per test

    @BeforeEach
    public void setup() {
        roomType = persistRoomType();
    }

    // ── the query under test ──────────────────────────────────────

    @Test
    public void findAvailableRooms_returnsRoom_whenNoReservationOrBlock() {
        Room room = persistRoom(roomType);

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(room);
    }

    @Test
    public void findAvailableRooms_excludesRoom_withOverlappingConfirmedReservation() {
        Room room = persistRoom(roomType);
        // Overlaps the stay (Jun 11 -> Jun 12)
        persistReservation(room, ReservationStatus.CONFIRMED,
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).isEmpty();
    }

    @Test
    public void findAvailableRooms_includesRoom_whenOverlappingReservationIsCancelled() {
        // This is the regression guard for the old bug: a CANCELLED reservation
        // must NOT block the room. The query excludes status = 'CANCELLED'.
        Room room = persistRoom(roomType);
        persistReservation(room, ReservationStatus.CANCELLED,
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(room);
    }

    @Test
    public void findAvailableRooms_includesRoom_whenReservationIsAdjacentNotOverlapping() {
        // Half-open intervals: a reservation that checks out ON our check-in day
        // does not overlap. Guest A leaves Jun 10 morning, Guest B arrives Jun 10.
        Room room = persistRoom(roomType);
        persistReservation(room, ReservationStatus.CONFIRMED,
                LocalDate.of(2026, 6, 7), CHECK_IN); // checkout == CHECK_IN

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(room);
    }

    @Test
    public void findAvailableRooms_excludesRoom_withOverlappingMaintenanceBlock() {
        Room room = persistRoom(roomType);
        persistMaintenanceBlock(room, LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 14));

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).isEmpty();
    }

    @Test
    public void findAvailableRooms_returnsOnlyFreeRooms_amongMany() {
        Room free = persistRoom(roomType);
        Room booked = persistRoom(roomType);
        Room blocked = persistRoom(roomType);
        persistReservation(booked, ReservationStatus.CONFIRMED, CHECK_IN, CHECK_OUT);
        persistMaintenanceBlock(blocked, CHECK_IN, CHECK_OUT);

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(free);
    }

    @Test
    public void findAvailableRooms_isScopedToRequestedRoomType() {
        Room mine = persistRoom(roomType);
        RoomType otherType = persistRoomType();
        persistRoom(otherType); // a free room of a different type

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(mine);
    }

    // ── fixtures ──────────────────────────────────────────────────

    private Hotel persistHotel() {
        seq++;
        return em.persistAndFlush(Hotel.builder()
                .name("Test Hotel " + seq)
                .address("1 Test St")
                .city("Cairo")
                .country("Egypt")
                .phone("phone-" + seq)          // unique constraint
                .email("hotel-" + seq + "@test.example") // unique constraint
                .starRating(5)
                .build());
    }

    private RoomType persistRoomType() {
        seq++;
        return em.persistAndFlush(RoomType.builder()
                .hotel(persistHotel())
                .name("Type " + seq)            // unique per (hotel, name)
                .maxOccupancy(2)
                .totalRooms(5)
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("EGP")
                .build());
    }

    private Room persistRoom(RoomType type) {
        seq++;
        return em.persistAndFlush(Room.builder()
                .roomType(type)
                .roomNumber(seq)                // unique per (roomType, roomNumber)
                .floor(1)
                .building("Main")
                .build());
    }

    private Guest persistGuest() {
        seq++;
        return em.persistAndFlush(Guest.builder()
                .firstName("Test")
                .lastName("Guest")
                .email("guest-" + seq + "@test.example") // unique constraint
                .password("hash")
                .phone("+200000" + seq)
                .nationality("EG")
                .documentType("PASSPORT")
                .documentNumber("DOC" + seq)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }

    private RatePlan persistRatePlan(RoomType type) {
        seq++;
        return em.persistAndFlush(RatePlan.builder()
                .roomType(type)
                .name("Plan " + seq)
                .minStayNights(1)
                .breakfastIncluded(false)
                .isRefundable(true)
                .build());
    }

    private Reservation persistReservation(Room room, ReservationStatus status,
                                           LocalDate checkIn, LocalDate checkOut) {
        seq++;
        return em.persistAndFlush(Reservation.builder()
                .guest(persistGuest())
                .ratePlan(persistRatePlan(room.getRoomType()))
                .assignedRoom(room)
                .confirmationNumber("CONF-" + seq) // unique constraint
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numGuests(2)
                .totalPrice(new BigDecimal("300.00"))
                .status(status)
                .build());
    }

    private MaintenanceBlock persistMaintenanceBlock(Room room, LocalDate start, LocalDate end) {
        return em.persistAndFlush(MaintenanceBlock.builder()
                .room(room)
                .startDate(start)
                .endDate(end)
                .reason("Repainting")
                .build());
    }
}
