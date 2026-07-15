package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.HotelRequestDTO;
import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;

    @Transactional(readOnly = true)
    public List<HotelResponseDTO> getAllHotels() {
        return hotelRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public HotelResponseDTO getHotelById(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));
        return toDTO(hotel);
    }

    @Transactional
    public HotelResponseDTO createHotel(HotelRequestDTO request) {
        Hotel hotel = Hotel.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .country(request.country())
                .phone(request.phone())
                .email(request.email())
                .starRating(request.starRating())
                .timezone(request.timezone())
                .createdAt(LocalDateTime.now())
                .build();
        return toDTO(hotelRepository.save(hotel));
    }

    @Transactional
    public HotelResponseDTO updateHotel(Long id, HotelRequestDTO request) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));

        hotel.setName(request.name());
        hotel.setAddress(request.address());
        hotel.setCity(request.city());
        hotel.setCountry(request.country());
        hotel.setPhone(request.phone());
        hotel.setEmail(request.email());
        hotel.setStarRating(request.starRating());
        hotel.setTimezone(request.timezone());

        return toDTO(hotelRepository.save(hotel));
    }

    @Transactional
    public void deleteHotel(Long id) {
        if (!hotelRepository.existsById(id)) {
            throw new ResourceNotFoundException("Hotel not found with ID: " + id);
        }
        hotelRepository.deleteById(id);
    }

    private HotelResponseDTO toDTO(Hotel hotel) {
        return new HotelResponseDTO(
                hotel.getId(),
                hotel.getName(),
                hotel.getCity(),
                hotel.getCountry(),
                hotel.getStarRating(),
                hotel.getPhone(),
                hotel.getEmail(),
                hotel.getTimezone()
        );
    }
}
