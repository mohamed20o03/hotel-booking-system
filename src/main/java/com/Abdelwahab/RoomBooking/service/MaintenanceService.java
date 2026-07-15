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
import com.Abdelwahab.RoomBooking.repository.MaintanceBlockRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

/**
 * Takes physical rooms in and out of service for maintenance.
 *
 * Blocking a room is not just a note against that room — it removes real
 * capacity, so it also decrements the room type's sellable allotment for every
 * blocked night. That keeps the count calendar (what search sells) in step with
 * the physical rooms (what check-in can assign), so the two can never disagree.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintanceBlockRepository maintenanceBlockRepository;
    private final RoomRepository roomRepository;
    private final InventoryService inventoryService;

    /**
     * Puts a room under maintenance for [startDate, endDate) and reduces the room
     * type's per-night capacity accordingly. Runs in one transaction: if any night
     * is already fully sold, decrementCapacity throws and the whole block is rolled
     * back (nothing is persisted, no capacity changes).
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
