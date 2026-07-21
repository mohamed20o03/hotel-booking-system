package com.Abdelwahab.RoomBooking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockRequestDTO;
import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.MaintananceBlockRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

/**
 * Takes physical rooms in and out of service for maintenance, keeping the sellable
 * inventory honest as it does so.
 *
 * <p><strong>Responsibility.</strong> Blocking a room is not merely a note against
 * that room — it removes real capacity. So this service also decrements the room
 * type's sellable allotment (via {@link InventoryService}) for every blocked night,
 * and restores it when the block is lifted. That keeps the count calendar (what
 * search sells) in step with the physical rooms (what check-in can assign), so the
 * two can never disagree.
 *
 * <p><strong>Concurrency.</strong> Capacity adjustments run through
 * {@link InventoryService}, which pessimistically locks the affected night rows; a
 * block that would collide with concurrent bookings serializes behind them. The
 * capacity change and the block record are written in the same transaction, so a
 * rejection leaves neither.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only
 * injected collaborators.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintananceBlockRepository maintenanceBlockRepository;
    private final RoomRepository roomRepository;
    private final InventoryService inventoryService;

    /**
     * Puts a room under maintenance for {@code [startDate, endDate)} and reduces the
     * room type's per-night capacity accordingly.
     *
     * <p>Rejects a block overlapping an existing one on the same physical room, since
     * capacity is decremented per block and overlapping blocks would drop the room
     * type's count by more than the single room actually taken offline. Read-write
     * transactional and atomic: the capacity decrement runs before the block is
     * persisted, so if any blocked night is already fully sold the decrement throws
     * and the whole operation rolls back — nothing is persisted and no capacity
     * changes.
     *
     * @param request the block to create (room id, start and end dates, reason);
     *                must be non-{@code null} and valid.
     * @return a {@link MaintenanceBlockResponseDTO} view of the persisted block.
     * @throws ResourceNotFoundException  if no room exists with the requested id.
     * @throws DuplicateResourceException if an overlapping block already exists on
     *         the same room.
     * @throws NoAvailabilityException    if a blocked night is fully booked (surfaced
     *         from {@link InventoryService#decrementCapacity}); rolls back the block.
     */
    @Transactional
    public MaintenanceBlockResponseDTO createBlock(MaintenanceBlockRequestDTO request) {
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found with ID: " + request.roomId()));
        RoomType roomType = room.getRoomType();

        // Reject a block that overlaps an existing one on the SAME physical room:
        // capacity is decremented per block, so overlapping blocks would drop the
        // room type's count by more than the one physical room actually taken offline.
        if (maintenanceBlockRepository.existsOverlappingBlock(
                room.getId(), request.startDate(), request.endDate())) {
            throw new DuplicateResourceException(String.format(
                    "Room %d already has a maintenance block overlapping %s to %s.",
                    room.getRoomNumber(), request.startDate(), request.endDate()));
        }

        // Reserve the physical room offline in the count calendar first; this throws
        // NoAvailabilityException (rolling back) if a blocked night is fully booked.
        inventoryService.decrementCapacity(roomType, request.startDate(), request.endDate());

        MaintenanceBlock block = maintenanceBlockRepository.save(MaintenanceBlock.builder()
                .room(room)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(request.reason())
                .build());

        return toDTO(block);
    }

    /**
     * Lifts a maintenance block and returns the freed capacity to the allotment.
     *
     * <p>Read-write transactional and atomic: the capacity is restored (via
     * {@link InventoryService#incrementCapacity}) and the block record deleted within
     * one transaction, so the count calendar and the block list stay consistent.
     *
     * @param blockId the maintenance block to lift; must identify an existing block.
     * @throws ResourceNotFoundException if no block exists with the given id.
     */
    @Transactional
    public void removeBlock(Long blockId) {
        MaintenanceBlock block = maintenanceBlockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance block not found with ID: " + blockId));

        inventoryService.incrementCapacity(
                block.getRoom().getRoomType(), block.getStartDate(), block.getEndDate());

        maintenanceBlockRepository.delete(block);
    }

    private MaintenanceBlockResponseDTO toDTO(MaintenanceBlock block) {
        Room room = block.getRoom();
        return new MaintenanceBlockResponseDTO(
                block.getId(),
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType().getName(),
                block.getStartDate(),
                block.getEndDate(),
                block.getReason());
    }
}
