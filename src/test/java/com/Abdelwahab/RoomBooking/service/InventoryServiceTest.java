package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.model.RoomTypeInventory;
import com.Abdelwahab.RoomBooking.repository.RoomTypeInventoryRepository;

/**
 * Plain Mockito unit test for InventoryService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); the RoomTypeInventoryRepository is a @Mock
 * injected into the service). It verifies the count-based, day-by-day inventory engine
 * in isolation: the non-locking availability reads (findForStay) that a stay is bookable
 * only when every night has a row with a free room, the bottleneck arithmetic behind
 * availableCount, and the locking mutations (lockForStay) — reserve, release, and the
 * maintenance capacity adjustments — that either apply to every night or fail wholesale
 * with NoAvailabilityException, never overselling. Concurrency semantics of the
 * pessimistic lock itself are proven in RoomTypeInventoryRepositoryTest; here the lock
 * call is mocked and only the per-night decision logic is under test.
 */
@ExtendWith(MockitoExtension.class)
public class InventoryServiceTest {

    @Mock private RoomTypeInventoryRepository inventoryRepository;
    @InjectMocks private InventoryService inventoryService;

    private RoomType roomType;

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 6, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 6, 4); // 3 nights: Jun 1,2,3

    @BeforeEach
    public void setup() {
        roomType = RoomType.builder().id(1L).name("Standard Double").totalRooms(3).build();
    }

    /** Builds the 3 inventory rows for the test stay, each with the given booked count. */
    private List<RoomTypeInventory> rows(int total, int... bookedPerNight) {
        List<RoomTypeInventory> list = new ArrayList<>();
        LocalDate d = CHECK_IN;
        for (int booked : bookedPerNight) {
            list.add(RoomTypeInventory.builder()
                    .roomType(roomType).date(d).totalRooms(total).bookedCount(booked).build());
            d = d.plusDays(1);
        }
        return list;
    }

    // ── availability (non-locking) ────────────────────────────────

    /**
     * Given three inventory rows for the three-night stay, each with a free room
     * (booked 0/1/2 of 3); when availability is checked; then the result is true.
     */
    @Test
    public void isAvailable_true_whenEveryNightHasRoom() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 1, 2));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isTrue();
    }

    /**
     * Given the middle night is sold out (booked 3 of 3) while the others have room;
     * when availability is checked; then the result is false — a single full night
     * makes the whole stay unavailable.
     */
    @Test
    public void isAvailable_false_whenAnyNightSoldOut() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 3, 1));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isFalse();
    }

    /**
     * Given only two inventory rows exist for a three-night stay (one night has no row);
     * when availability is checked; then the result is false — a missing night is treated
     * as not open for booking, not as free.
     */
    @Test
    public void isAvailable_false_whenANightIsNotOpen() {
        // Only 2 rows returned for a 3-night stay -> one night has no inventory row
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 0));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isFalse();
    }

    /**
     * Given remaining rooms per night of 3, 1, 2;
     * when the available count is computed; then it is 1 — the minimum across nights,
     * since the scarcest night bottlenecks the whole stay.
     */
    @Test
    public void availableCount_isMinRemainingAcrossNights() {
        // remaining per night: 3, 1, 2  -> bottleneck is 1
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 2, 1));
        assertThat(inventoryService.availableCount(1L, CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    /**
     * Given a night with no inventory row for the stay;
     * when the available count is computed; then it is zero — a night that is not open
     * caps availability at nothing.
     */
    @Test
    public void availableCount_zero_whenANightNotOpen() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 0));
        assertThat(inventoryService.availableCount(1L, CHECK_IN, CHECK_OUT)).isZero();
    }

    // ── reserve (locking) ─────────────────────────────────────────

    /**
     * Given the pessimistically locked rows (lockForStay) have room on every night
     * (booked 0/1/2 of 3); when the stay is reserved; then each night's booked count is
     * incremented (to 1/2/3) and all three rows are persisted via saveAll.
     */
    @Test
    public void reserve_incrementsEveryNight_whenAvailable() {
        List<RoomTypeInventory> locked = rows(3, 0, 1, 2);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.reserve(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getBookedCount).containsExactly(1, 2, 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoomTypeInventory>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    /**
     * Given the locked rows show the middle night full (booked == total);
     * when a reservation is attempted; then a NoAvailabilityException ("sold out") is
     * raised and nothing is saved — the reserve is all-or-nothing across the stay.
     */
    @Test
    public void reserve_throws_whenANightIsSoldOut() {
        // Middle night is full (booked == total)
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 3, 1));

        assertThatThrownBy(() -> inventoryService.reserve(roomType, CHECK_IN, CHECK_OUT))
                .isInstanceOf(NoAvailabilityException.class)
                .hasMessageContaining("sold out");

        verify(inventoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * Given only two locked rows exist for a three-night stay;
     * when a reservation is attempted; then a NoAvailabilityException ("not open for
     * booking") is raised and nothing is saved — every night must have an open row.
     */
    @Test
    public void reserve_throws_whenDatesNotFullyOpen() {
        // Only 2 rows for a 3-night stay
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 0));

        assertThatThrownBy(() -> inventoryService.reserve(roomType, CHECK_IN, CHECK_OUT))
                .isInstanceOf(NoAvailabilityException.class)
                .hasMessageContaining("not open for booking");

        verify(inventoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    // ── release ───────────────────────────────────────────────────

    /**
     * Given locked rows with booked counts 1/2/3;
     * when the stay is released; then each night's booked count is decremented (to 0/1/2)
     * and the rows are persisted.
     */
    @Test
    public void release_decrementsEveryNight() {
        List<RoomTypeInventory> locked = rows(3, 1, 2, 3);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.release(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getBookedCount).containsExactly(0, 1, 2);
        verify(inventoryRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * Given locked rows already at zero booked;
     * when the stay is released; then every night's booked count stays at zero — the
     * decrement is floored and never produces a negative count.
     */
    @Test
    public void release_neverGoesBelowZero() {
        List<RoomTypeInventory> locked = rows(3, 0, 0, 0);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.release(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getBookedCount).containsExactly(0, 0, 0);
    }

    // ── capacity (maintenance) ────────────────────────────────────

    /**
     * Given locked rows where every night has a free room (booked 0/1/2 of 3);
     * when a maintenance block decrements capacity; then each night's total drops by one
     * (to 2/2/2) and the rows are persisted — capacity can be withdrawn without
     * overselling any night.
     */
    @Test
    public void decrementCapacity_reducesTotalOnEveryNight_whenRoomsAreFree() {
        // total 3, booked 0/1/2 -> all have room, so capacity can drop to 2/2/2
        List<RoomTypeInventory> locked = rows(3, 0, 1, 2);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.decrementCapacity(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getTotalRooms).containsExactly(2, 2, 2);
        verify(inventoryRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * Given the locked rows show the middle night fully booked (booked == total);
     * when a maintenance block attempts to decrement capacity; then a
     * NoAvailabilityException ("all rooms are booked") is raised and nothing is saved —
     * capacity cannot be withdrawn from a night that would then be oversold.
     */
    @Test
    public void decrementCapacity_throws_whenANightIsFullyBooked() {
        // Middle night sold out (booked == total): dropping capacity would oversell
        List<RoomTypeInventory> locked = rows(3, 0, 3, 1);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        assertThatThrownBy(() -> inventoryService.decrementCapacity(roomType, CHECK_IN, CHECK_OUT))
                .isInstanceOf(NoAvailabilityException.class)
                .hasMessageContaining("all rooms are booked");

        verify(inventoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * Given locked rows with total capacity 2 on every night;
     * when a maintenance block is lifted and capacity is incremented; then each night's
     * total rises by one (to 3/3/3) and the rows are persisted.
     */
    @Test
    public void incrementCapacity_raisesTotalOnEveryNight() {
        List<RoomTypeInventory> locked = rows(2, 1, 1, 1);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.incrementCapacity(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getTotalRooms).containsExactly(3, 3, 3);
        verify(inventoryRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
