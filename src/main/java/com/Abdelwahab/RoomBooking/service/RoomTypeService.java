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

/**
 * Manages the room types offered by a hotel — the sellable categories (base price,
 * occupancy, currency, and total physical rooms) that rate plans price and
 * inventory tracks.
 *
 * <p><strong>Responsibility.</strong> CRUD over {@link RoomType}, always scoped to
 * the owning hotel in the request path. Every write resolves the room type within
 * its hotel, so {@code /hotels/1/room-types/{id}} can never mutate a room type that
 * belongs to a different hotel.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repositories; safe for concurrent request threads.
 */
@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;

    /**
     * Lists every room type belonging to the given hotel.
     *
     * <p>Read-only transactional: a pure query, marked {@code readOnly} to skip
     * dirty-checking overhead.
     *
     * @param hotelId the owning hotel; must identify an existing hotel.
     * @return the hotel's room types as {@link RoomTypeResponseDTO} views; an empty
     *         list if it has none.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
    @Transactional(readOnly = true)
    public List<RoomTypeResponseDTO> getRoomTypesByHotel(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel not found with ID: " + hotelId);
        }

        return roomTypeRepository.findByHotelId(hotelId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Creates a room type under the given hotel.
     *
     * <p>Read-write transactional: the insert commits atomically.
     *
     * @param hotelId the owning hotel; must identify an existing hotel.
     * @param request the room type's attributes (name, occupancy, base price,
     *                currency, total rooms); must be non-{@code null} and valid.
     * @return a {@link RoomTypeResponseDTO} view of the persisted room type,
     *         including its generated id.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
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

    /**
     * Updates a room type's attributes in place, scoped to its owning hotel.
     *
     * <p>Read-write transactional: the resolved entity is mutated and flushed on
     * commit via JPA dirty-checking.
     *
     * @param hotelId    the hotel the room type must belong to.
     * @param roomTypeId the room type to update.
     * @param request    the replacement attribute values; must be non-{@code null}
     *                   and valid.
     * @return a {@link RoomTypeResponseDTO} view of the updated room type.
     * @throws ResourceNotFoundException if the room type does not exist or does not
     *         belong to the given hotel; thrown before any mutation.
     */
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

    /**
     * Deletes a room type, scoped to its owning hotel.
     *
     * <p>Read-write transactional. The deletion may still be refused at the database
     * layer if dependent records (rate plans, inventory, reservations) reference the
     * room type and the foreign keys forbid orphaning.
     *
     * @param hotelId    the hotel the room type must belong to.
     * @param roomTypeId the room type to delete.
     * @throws ResourceNotFoundException if the room type does not exist or does not
     *         belong to the given hotel.
     */
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
