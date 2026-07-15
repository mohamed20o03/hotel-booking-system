package com.Abdelwahab.RoomBooking.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.model.RoomTypeInventory;

/**
 * Repository test for RoomTypeInventoryRepository.
 *
 * findForStay / lockForStay are hand-written JPQL, and lockForStay carries the
 * pessimistic lock the whole double-booking guarantee rests on. Only a real
 * database proves either; the concurrency test needs two live transactions.
 *
 * See RoomRepositoryTest for why @AutoConfigureTestDatabase(replace = NONE) and a
 * dedicated H2 URL are used (schema.sql + validate; isolate from other contexts).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(
    properties = "spring.datasource.url=jdbc:h2:mem:inventoryrepotest;DB_CLOSE_DELAY=-1")
public class RoomTypeInventoryRepositoryTest {

    @Autowired private RoomTypeInventoryRepository inventoryRepository;
    @Autowired private TestEntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 6, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 6, 13); // nights 10, 11, 12
    private static final LocalDate NIGHT_11 = LocalDate.of(2026, 6, 11);
    private static final LocalDate NIGHT_12 = LocalDate.of(2026, 6, 12);

    private RoomType roomType;
    // Static so fixture values stay unique across tests: the concurrency test
    // COMMITS its data, so a per-instance counter would regenerate the same
    // unique phone/email and collide on the next test's setup.
    private static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeEach
    public void setup() {
        roomType = persistRoomType();
    }

    // ── findForStay ───────────────────────────────────────────────

    @Test
    public void findForStay_returnsOneRowPerNight_inTheHalfOpenInterval() {
        persistInventory(roomType, CHECK_IN, 5, 0);
        persistInventory(roomType, NIGHT_11, 5, 0);
        persistInventory(roomType, NIGHT_12, 5, 0);
        persistInventory(roomType, CHECK_OUT, 5, 0); // checkout night, must be excluded

        List<RoomTypeInventory> rows = inventoryRepository.findForStay(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(rows).extracting(RoomTypeInventory::getDate)
                .containsExactlyInAnyOrder(CHECK_IN, NIGHT_11, NIGHT_12); // 3 nights, not the 13th
    }

    @Test
    public void findForStay_returnsFewerRows_whenANightIsNotOpen() {
        // Only 2 of the 3 nights are open -> the gap the service turns into "unavailable"
        persistInventory(roomType, CHECK_IN, 5, 0);
        persistInventory(roomType, NIGHT_12, 5, 0);

        List<RoomTypeInventory> rows = inventoryRepository.findForStay(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(rows).hasSize(2);
    }

    @Test
    public void findForStay_isScopedToRoomType() {
        persistInventory(roomType, CHECK_IN, 5, 0);
        RoomType other = persistRoomType();
        persistInventory(other, CHECK_IN, 5, 0);

        List<RoomTypeInventory> rows = inventoryRepository.findForStay(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoomType().getId()).isEqualTo(roomType.getId());
        assertThat(rows.get(0).getRoomType().getId()).isNotEqualTo(other.getId());

    }

    // ── lockForStay ───────────────────────────────────────────────

    @Test
    public void lockForStay_returnsRowsOrderedByDate() {
        // Persist out of order; the query's ORDER BY date must still sort them.
        persistInventory(roomType, NIGHT_12, 5, 0);
        persistInventory(roomType, CHECK_IN, 5, 0);
        persistInventory(roomType, NIGHT_11, 5, 0);

        List<RoomTypeInventory> rows = inventoryRepository.lockForStay(roomType.getId(), CHECK_IN, CHECK_OUT);

        assertThat(rows).extracting(RoomTypeInventory::getDate)
                .containsExactly(CHECK_IN, NIGHT_11, NIGHT_12);
    }

    /**
     * The core Phase 2 guarantee: two transactions racing for the LAST room must
     * serialize, so exactly one wins and inventory is never oversold.
     *
     * Capacity = 1 on every night. Two threads each run the reserve pattern in
     * their own transaction: lockForStay -> if every night has room, increment;
     * else back off. Because lockForStay issues SELECT ... FOR UPDATE, the second
     * thread blocks on the row until the first commits, then sees booked_count = 1
     * and loses. Without the lock, both would read 0 and both write 1 (oversell).
     *
     * The seed must be COMMITTED (TestTransaction) so the worker transactions,
     * which run on their own connections, can see it.
     */
    @Test
    public void lockForStay_serializesConcurrentBookings_soLastRoomIsSoldOnce() throws Exception {
        persistInventory(roomType, CHECK_IN, 1, 0);
        persistInventory(roomType, NIGHT_11, 1, 0);
        persistInventory(roomType, NIGHT_12, 1, 0);
        commitSeed();

        Long typeId = roomType.getId();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        CyclicBarrier startLine = new CyclicBarrier(2); // release both threads together

        Runnable booker = () -> {
            try {
                startLine.await();
                tx.executeWithoutResult(status -> {
                    List<RoomTypeInventory> rows = inventoryRepository.lockForStay(typeId, CHECK_IN, CHECK_OUT);
                    boolean allFree = rows.stream().allMatch(r -> r.remaining() > 0);
                    if (!allFree) {
                        soldOut.incrementAndGet();
                        return;
                    }
                    rows.forEach(r -> r.setBookedCount(r.getBookedCount() + 1));
                    inventoryRepository.saveAll(rows);
                    successes.incrementAndGet();
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(booker);
        pool.submit(booker);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Exactly one booking won, the other found it sold out — no oversell.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(soldOut.get()).isEqualTo(1);

        // And the committed state confirms it: the last room was sold exactly once.
        int booked = readBookedCount(typeId, CHECK_IN);
        assertThat(booked).isEqualTo(1);
    }

    // ── fixtures / helpers ────────────────────────────────────────

    /** Commits the data staged in the test's transaction so other connections see it. */
    private void commitSeed() {
        em.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private int readBookedCount(Long typeId, LocalDate date) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return tx.execute(status -> inventoryRepository.findForStay(typeId, date, date.plusDays(1))
                .get(0).getBookedCount());
    }

    private Hotel persistHotel() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(Hotel.builder()
                .name("Test Hotel " + n)
                .address("1 Test St")
                .city("Cairo")
                .country("Egypt")
                .phone("phone-" + n)                       // unique
                .email("hotel-" + n + "@test.example")     // unique
                .starRating(5)
                .build());
    }

    private RoomType persistRoomType() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(RoomType.builder()
                .hotel(persistHotel())
                .name("Type " + n)                         // unique per (hotel, name)
                .maxOccupancy(2)
                .totalRooms(5)
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("EGP")
                .build());
    }

    private void persistInventory(RoomType type, LocalDate date, int total, int booked) {
        em.persistAndFlush(RoomTypeInventory.builder()
                .roomType(type)
                .date(date)
                .totalRooms(total)
                .bookedCount(booked)
                .build());
    }
}
