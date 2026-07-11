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

    @Test
    public void isAvailable_true_whenEveryNightHasRoom() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 1, 2));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isTrue();
    }

    @Test
    public void isAvailable_false_whenAnyNightSoldOut() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 3, 1));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isFalse();
    }

    @Test
    public void isAvailable_false_whenANightIsNotOpen() {
        // Only 2 rows returned for a 3-night stay -> one night has no inventory row
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 0));
        assertThat(inventoryService.isAvailable(1L, CHECK_IN, CHECK_OUT)).isFalse();
    }

    @Test
    public void availableCount_isMinRemainingAcrossNights() {
        // remaining per night: 3, 1, 2  -> bottleneck is 1
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 2, 1));
        assertThat(inventoryService.availableCount(1L, CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    @Test
    public void availableCount_zero_whenANightNotOpen() {
        when(inventoryRepository.findForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 0));
        assertThat(inventoryService.availableCount(1L, CHECK_IN, CHECK_OUT)).isZero();
    }

    // ── reserve (locking) ─────────────────────────────────────────

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

    @Test
    public void reserve_throws_whenANightIsSoldOut() {
        // Middle night is full (booked == total)
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(rows(3, 0, 3, 1));

        assertThatThrownBy(() -> inventoryService.reserve(roomType, CHECK_IN, CHECK_OUT))
                .isInstanceOf(NoAvailabilityException.class)
                .hasMessageContaining("sold out");

        verify(inventoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

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

    @Test
    public void release_decrementsEveryNight() {
        List<RoomTypeInventory> locked = rows(3, 1, 2, 3);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.release(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getBookedCount).containsExactly(0, 1, 2);
        verify(inventoryRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void release_neverGoesBelowZero() {
        List<RoomTypeInventory> locked = rows(3, 0, 0, 0);
        when(inventoryRepository.lockForStay(1L, CHECK_IN, CHECK_OUT)).thenReturn(locked);

        inventoryService.release(roomType, CHECK_IN, CHECK_OUT);

        assertThat(locked).extracting(RoomTypeInventory::getBookedCount).containsExactly(0, 0, 0);
    }
}
