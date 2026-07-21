package com.Abdelwahab.RoomBooking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.AbstractIntegrationTest;
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
 * Runs against a real PostgreSQL container provided by {@link AbstractIntegrationTest}.
 * Each test is wrapped in a transaction that rolls back on completion, keeping the
 * shared database clean between tests.
 */
@Transactional
public class RoomRepositoryTest extends AbstractIntegrationTest {

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

    /**
     * Given a single room with no reservation and no maintenance block over the stay;
     * when available rooms are queried; then that room is returned — the baseline case
     * where both NOT EXISTS subqueries pass.
     */
    @Test
    public void findAvailableRooms_returnsRoom_whenNoReservationOrBlock() {
        Room room = persistRoom(roomType);

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).containsExactly(room);
    }

    /**
     * Given a room with a CONFIRMED reservation overlapping the stay;
     * when available rooms are queried; then the result is empty — the reservation
     * NOT EXISTS subquery excludes it.
     */
    @Test
    public void findAvailableRooms_excludesRoom_withOverlappingConfirmedReservation() {
        Room room = persistRoom(roomType);
        // Overlaps the stay (Jun 11 -> Jun 12)
        persistReservation(room, ReservationStatus.CONFIRMED,
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).isEmpty();
    }

    /**
     * Given a room whose only overlapping reservation is CANCELLED;
     * when available rooms are queried; then the room is returned — the query excludes
     * CANCELLED reservations. This is the regression guard for the old bug where a
     * cancelled booking still blocked the room.
     */
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

    /**
     * Given a room with a reservation that checks out exactly on the new stay's check-in
     * day; when available rooms are queried; then the room is returned — under half-open
     * intervals an adjacent, back-to-back reservation does not overlap.
     */
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

    /**
     * Given a room with a maintenance block overlapping the stay;
     * when available rooms are queried; then the result is empty — the maintenance
     * NOT EXISTS subquery excludes it.
     */
    @Test
    public void findAvailableRooms_excludesRoom_withOverlappingMaintenanceBlock() {
        Room room = persistRoom(roomType);
        persistMaintenanceBlock(room, LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 14));

        List<Room> result = roomRepository.findAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(result).isEmpty();
    }

    /**
     * Given three rooms of the same type — one free, one with a confirmed reservation,
     * one under maintenance — over the stay; when available rooms are queried; then only
     * the free room is returned, exercising both exclusion subqueries together.
     */
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

    /**
     * Given a free room of the requested type and another free room of a different type;
     * when available rooms are queried for the requested type; then only its room is
     * returned — the query is scoped to the requested room type.
     */
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
