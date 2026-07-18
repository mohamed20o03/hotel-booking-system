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

/**
 * Manages the hotel catalogue — the top-level property records under which room
 * types, rate plans, and inventory all hang.
 *
 * <p><strong>Responsibility.</strong> Standard CRUD over the {@link Hotel}
 * aggregate root. Browsing is public; mutations are administrative, gated by the
 * {@code /api/hotels/**} rules in {@code SecurityConfig} and {@code @PreAuthorize}
 * on the controller.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repository; safe for concurrent request threads.
 */
@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;

    /**
     * Lists every hotel in the catalogue.
     *
     * <p>Read-only transactional: a pure query, marked {@code readOnly} to skip
     * dirty-checking overhead.
     *
     * @return all hotels as {@link HotelResponseDTO} views; an empty list if none
     *         exist.
     */
    @Transactional(readOnly = true)
    public List<HotelResponseDTO> getAllHotels() {
        return hotelRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Retrieves a single hotel by primary key.
     *
     * <p>Read-only transactional: a pure lookup performing no writes.
     *
     * @param id the hotel's primary key; must not be {@code null}.
     * @return the matching {@link HotelResponseDTO}.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
    @Transactional(readOnly = true)
    public HotelResponseDTO getHotelById(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + id));
        return toDTO(hotel);
    }

    /**
     * Creates a new hotel from the supplied details, stamping its creation time.
     *
     * <p>Read-write transactional: the insert commits atomically.
     *
     * @param request the hotel's descriptive fields; must be non-{@code null} and
     *                valid per the DTO constraints.
     * @return a {@link HotelResponseDTO} view of the persisted hotel, including its
     *         generated id.
     */
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

    /**
     * Updates an existing hotel's descriptive fields in place.
     *
     * <p>Read-write transactional: the loaded entity is mutated and flushed within
     * the transaction (JPA dirty-checking persists the changes on commit).
     *
     * @param id      the hotel to update; must identify an existing record.
     * @param request the replacement field values; must be non-{@code null} and valid.
     * @return a {@link HotelResponseDTO} view of the updated hotel.
     * @throws ResourceNotFoundException if no hotel exists with the given id; thrown
     *         before any mutation.
     */
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

    /**
     * Deletes a hotel by primary key.
     *
     * <p>Read-write transactional. Note the deletion may still be refused at the
     * database layer if dependent records (room types, rate plans, inventory)
     * reference the hotel and the foreign keys forbid orphaning.
     *
     * @param id the hotel to delete; must identify an existing record.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
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
