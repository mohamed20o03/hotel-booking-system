package com.Abdelwahab.RoomBooking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;

/**
 * Repository test for MaintanceBlockRepository.existsOverlappingBlock — the
 * half-open overlap query (existing.start < new.end AND existing.end > new.start).
 * The boundary behaviour (touching vs. truly overlapping, and per-room scoping)
 * is exactly what only a real database confirms; a mocked boolean proves nothing.
 *
 * See RoomRepositoryTest for why @AutoConfigureTestDatabase(replace = NONE) and a
 * dedicated H2 URL are used.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(
    properties = "spring.datasource.url=jdbc:h2:mem:maintenancerepotest;DB_CLOSE_DELAY=-1")
public class MaintenanceBlockRepositoryTest {

    @Autowired private MaintanceBlockRepository maintenanceBlockRepository;
    @Autowired private TestEntityManager em;

    // Static so unique fixture fields never collide across tests.
    private static final AtomicInteger SEQ = new AtomicInteger();

    // Existing block covers Jun 10, 11, 12 (checkout Jun 13 is not blocked).
    private static final LocalDate BLOCK_START = LocalDate.of(2026, 6, 10);
    private static final LocalDate BLOCK_END = LocalDate.of(2026, 6, 13);

    private Room room;

    @BeforeEach
    public void setup() {
        room = persistRoom(persistRoomType());
        persistBlock(room, BLOCK_START, BLOCK_END);
    }

    @Test
    public void overlaps_whenNewRangeIsIdentical() {
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(room.getId(), BLOCK_START, BLOCK_END))
                .isTrue();
    }

    @Test
    public void overlaps_whenNewRangeStartsInsideExisting() {
        // Jun 12 -> Jun 15 : Jun 12 is shared
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 15)))
                .isTrue();
    }

    @Test
    public void overlaps_whenNewRangeFullyContainsExisting() {
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .isTrue();
    }

    @Test
    public void noOverlap_whenNewRangeStartsExactlyAtExistingEnd() {
        // Existing is [10, 13); new [13, 15) only touches the boundary -> not overlapping
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 15)))
                .isFalse();
    }

    @Test
    public void noOverlap_whenNewRangeEndsExactlyAtExistingStart() {
        // new [7, 10) ends where existing [10, 13) starts -> not overlapping
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 10)))
                .isFalse();
    }

    @Test
    public void noOverlap_whenNewRangeIsEntirelyBefore() {
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)))
                .isFalse();
    }

    @Test
    public void noOverlap_whenBlockIsOnADifferentRoom() {
        Room otherRoom = persistRoom(room.getRoomType());
        // Same dates as the existing block, but a different physical room
        assertThat(maintenanceBlockRepository.existsOverlappingBlock(
                otherRoom.getId(), BLOCK_START, BLOCK_END))
                .isFalse();
    }

    // ── fixtures ──────────────────────────────────────────────────

    private MaintenanceBlock persistBlock(Room r, LocalDate start, LocalDate end) {
        return em.persistAndFlush(MaintenanceBlock.builder()
                .room(r)
                .startDate(start)
                .endDate(end)
                .reason("Repainting")
                .build());
    }

    private Room persistRoom(RoomType type) {
        return em.persistAndFlush(Room.builder()
                .roomType(type)
                .roomNumber(SEQ.incrementAndGet())   // unique per (roomType, roomNumber)
                .floor(1)
                .building("Main")
                .build());
    }

    private RoomType persistRoomType() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(RoomType.builder()
                .hotel(persistHotel())
                .name("Type " + n)
                .maxOccupancy(2)
                .totalRooms(5)
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("EGP")
                .build());
    }

    private Hotel persistHotel() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(Hotel.builder()
                .name("Test Hotel " + n)
                .address("1 Test St")
                .city("Cairo")
                .country("Egypt")
                .phone("phone-" + n)                     // unique
                .email("hotel-" + n + "@test.example")   // unique
                .starRating(5)
                .build());
    }
}
