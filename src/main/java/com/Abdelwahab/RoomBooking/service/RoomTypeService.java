package com.Abdelwahab.RoomBooking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
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

    private RoomTypeResponseDTO toDTO(RoomType roomType) {
        return new RoomTypeResponseDTO(
                roomType.getId(),
                roomType.getName(),
                roomType.getDescription(),
                roomType.getMaxOccupancy(),
                roomType.getTotalRooms(),
                roomType.getBasePricePerNight()
        );
    }
}
