package com.Abdelwahab.RoomBooking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.RoomTypeRequestDTO;
import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;
import com.Abdelwahab.RoomBooking.repository.RoomTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;

    @Transactional(readOnly = true)
    public List<RoomTypeResponseDTO> getRoomTypesByHotel(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel not found with ID: " + hotelId);
        }

        return roomTypeRepository.findByHotelId(hotelId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public RoomTypeResponseDTO createRoomType(Long hotelId, RoomTypeRequestDTO request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        RoomType roomType = RoomType.builder()
                .hotel(hotel)
                .name(request.name())
                .description(request.description())
                .maxOccupancy(request.maxOccupancy())
                .totalRooms(request.totalRooms())
                .basePricePerNight(request.basePricePerNight())
                .currency(request.currency())
                .build();
        return toDTO(roomTypeRepository.save(roomType));
    }

    @Transactional
    public RoomTypeResponseDTO updateRoomType(Long hotelId, Long roomTypeId, RoomTypeRequestDTO request) {
        RoomType roomType = resolveWithinHotel(hotelId, roomTypeId);

        roomType.setName(request.name());
        roomType.setDescription(request.description());
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setTotalRooms(request.totalRooms());
        roomType.setBasePricePerNight(request.basePricePerNight());
        roomType.setCurrency(request.currency());

        return toDTO(roomTypeRepository.save(roomType));
    }

    @Transactional
    public void deleteRoomType(Long hotelId, Long roomTypeId) {
        RoomType roomType = resolveWithinHotel(hotelId, roomTypeId);
        roomTypeRepository.delete(roomType);
    }

    /**
     * Loads a room type and asserts it actually belongs to the hotel in the path,
     * so /hotels/1/room-types/{id} can never mutate another hotel's room type.
     */
    private RoomType resolveWithinHotel(Long hotelId, Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + roomTypeId));
        if (!roomType.getHotel().getId().equals(hotelId)) {
            throw new ResourceNotFoundException(String.format(
                    "Room type %d does not belong to hotel %d.", roomTypeId, hotelId));
        }
        return roomType;
    }

    private RoomTypeResponseDTO toDTO(RoomType roomType) {
        return new RoomTypeResponseDTO(
                roomType.getId(),
                roomType.getName(),
                roomType.getDescription(),
                roomType.getMaxOccupancy(),
                roomType.getTotalRooms(),
                roomType.getBasePricePerNight(),
                roomType.getCurrency()
        );
    }
}
