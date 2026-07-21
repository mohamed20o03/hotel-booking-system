package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockRequestDTO;
import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.MaintananceBlockRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;

/**
 * Unit test for MaintenanceService — verifies that blocking/unblocking a room
 * also moves the room type's sellable capacity, and that a block over a fully
 * booked night is rejected (nothing persisted). InventoryService is mocked; its
 * own decrement/increment logic is covered in InventoryServiceTest.
 */
@ExtendWith(MockitoExtension.class)
public class MaintenanceServiceTest {

    @Mock private MaintananceBlockRepository maintenanceBlockRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private InventoryService inventoryService;
    @InjectMocks private MaintenanceService maintenanceService;

    private static final LocalDate START = LocalDate.of(2026, 6, 10);
    private static final LocalDate END = LocalDate.of(2026, 6, 13);

    private RoomType roomType;
    private Room room;

    @BeforeEach
    public void setup() {
        roomType = RoomType.builder().id(1L).name("Standard Double").totalRooms(3).build();
        room = Room.builder().id(7L).roomNumber(101).roomType(roomType).build();
    }

    /**
     * Given the room exists, no overlapping block is present, and the repository assigns
     * an id on save; when a maintenance block is created; then InventoryService is asked
     * to decrement capacity for the window, the block is persisted, and the returned DTO
     * carries the block id and the room's identifying details.
     */
    @Test
    public void createBlock_decrementsCapacityAndPersistsBlock() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(maintenanceBlockRepository.existsOverlappingBlock(7L, START, END)).thenReturn(false);
        when(maintenanceBlockRepository.save(any(MaintenanceBlock.class)))
                .thenAnswer(inv -> {
                    MaintenanceBlock b = inv.getArgument(0);
                    b.setId(99L);
                    return b;
                });

        MaintenanceBlockResponseDTO dto = maintenanceService.createBlock(
                new MaintenanceBlockRequestDTO(7L, START, END, "Repainting"));

        verify(inventoryService).decrementCapacity(roomType, START, END);
        verify(maintenanceBlockRepository).save(any(MaintenanceBlock.class));
        assertThat(dto.id()).isEqualTo(99L);
        assertThat(dto.roomId()).isEqualTo(7L);
        assertThat(dto.roomNumber()).isEqualTo(101);
        assertThat(dto.roomTypeName()).isEqualTo("Standard Double");
    }

    /**
     * Given the room exists and no block overlaps, but InventoryService rejects the
     * capacity decrement because a blocked night is sold out; when the block is created;
     * then the NoAvailabilityException propagates and the block is never saved — the
     * write is abandoned when capacity cannot be reserved.
     */
    @Test
    public void createBlock_throwsAndPersistsNothing_whenANightIsFullyBooked() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(maintenanceBlockRepository.existsOverlappingBlock(7L, START, END)).thenReturn(false);
        // Capacity drop rejected because a blocked night is sold out
        org.mockito.Mockito.doThrow(new NoAvailabilityException("all rooms are booked"))
                .when(inventoryService).decrementCapacity(roomType, START, END);

        assertThatThrownBy(() -> maintenanceService.createBlock(
                new MaintenanceBlockRequestDTO(7L, START, END, "Repainting")))
                .isInstanceOf(NoAvailabilityException.class);

        // The block must NOT be saved if capacity couldn't be reserved
        verify(maintenanceBlockRepository, never()).save(any(MaintenanceBlock.class));
    }

    /**
     * Given the room exists but an overlapping block already covers the window;
     * when a block is created; then a DuplicateResourceException is raised and neither
     * capacity nor the block is touched — the overlap guard runs before any mutation.
     */
    @Test
    public void createBlock_throwsAndPersistsNothing_whenAnOverlappingBlockExists() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(maintenanceBlockRepository.existsOverlappingBlock(7L, START, END)).thenReturn(true);

        assertThatThrownBy(() -> maintenanceService.createBlock(
                new MaintenanceBlockRequestDTO(7L, START, END, "Repainting")))
                .isInstanceOf(DuplicateResourceException.class);

        // Neither capacity nor the block is touched when it overlaps an existing block
        verify(inventoryService, never()).decrementCapacity(any(), any(), any());
        verify(maintenanceBlockRepository, never()).save(any(MaintenanceBlock.class));
    }

    /**
     * Given the room does not exist;
     * when a block is created for it; then a ResourceNotFoundException is raised and
     * neither capacity nor the block is touched.
     */
    @Test
    public void createBlock_throws_whenRoomNotFound() {
        when(roomRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceService.createBlock(
                new MaintenanceBlockRequestDTO(7L, START, END, "Repainting")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryService, never()).decrementCapacity(any(), any(), any());
        verify(maintenanceBlockRepository, never()).save(any(MaintenanceBlock.class));
    }

    /**
     * Given the block exists;
     * when it is removed; then InventoryService is asked to restore capacity for the
     * window and the block is deleted — lifting a block returns the rooms to sale.
     */
    @Test
    public void removeBlock_incrementsCapacityAndDeletesBlock() {
        MaintenanceBlock block = MaintenanceBlock.builder()
                .id(99L).room(room).startDate(START).endDate(END).reason("Repainting").build();
        when(maintenanceBlockRepository.findById(99L)).thenReturn(Optional.of(block));

        maintenanceService.removeBlock(99L);

        verify(inventoryService).incrementCapacity(roomType, START, END);
        verify(maintenanceBlockRepository).delete(block);
    }

    /**
     * Given the block does not exist;
     * when a removal is attempted; then a ResourceNotFoundException is raised and neither
     * capacity nor the delete is touched.
     */
    @Test
    public void removeBlock_throws_whenBlockNotFound() {
        when(maintenanceBlockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceService.removeBlock(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inventoryService, never()).incrementCapacity(any(), any(), any());
        verify(maintenanceBlockRepository, never()).delete(any(MaintenanceBlock.class));
    }
}
