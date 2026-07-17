package com.Abdelwahab.RoomBooking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.AddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.AddonResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Addon;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.repository.AddonRepository;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;

import lombok.RequiredArgsConstructor;

/**
 * Manages a hotel's catalogue of optional add-ons (airport transfer, spa, etc.).
 *
 * Reads (browsing available add-ons) are public; writes are admin-only, gated by
 * the /api/hotels/** rules in SecurityConfig plus @PreAuthorize on the controller.
 * Every write is scoped to the hotel in the path, so one hotel's add-on can never
 * be mutated through another hotel's URL.
 */
@Service
@RequiredArgsConstructor
public class AddonService {

    private final AddonRepository addonRepository;
    private final HotelRepository hotelRepository;

    /** Add-ons a guest can actually book right now — available only. */
    @Transactional(readOnly = true)
    public List<AddonResponseDTO> getAvailableAddons(Long hotelId) {
        requireHotel(hotelId);
        return addonRepository.findByHotelIdAndAvailableTrue(hotelId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public AddonResponseDTO createAddon(Long hotelId, AddonRequestDTO request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        Addon addon = Addon.builder()
                .hotel(hotel)
                .name(request.name())
                .category(request.category())
                .price(request.price())
                .priceUnit(request.priceUnit())
                .available(request.available() == null ? Boolean.TRUE : request.available())
                .build();
        return toDTO(addonRepository.save(addon));
    }

    @Transactional
    public AddonResponseDTO updateAddon(Long hotelId, Long addonId, AddonRequestDTO request) {
        Addon addon = resolveWithinHotel(hotelId, addonId);

        addon.setName(request.name());
        addon.setCategory(request.category());
        addon.setPrice(request.price());
        addon.setPriceUnit(request.priceUnit());
        if (request.available() != null) {
            addon.setAvailable(request.available());
        }
        return toDTO(addonRepository.save(addon));
    }

    @Transactional
    public void deleteAddon(Long hotelId, Long addonId) {
        Addon addon = resolveWithinHotel(hotelId, addonId);
        // A reservation_addon FK with ON DELETE RESTRICT blocks deletion of an
        // add-on still referenced by a booking — the DB is the final guard.
        addonRepository.delete(addon);
    }

    private void requireHotel(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel not found with ID: " + hotelId);
        }
    }

    /**
     * Loads an add-on and asserts it belongs to the hotel in the path, so
     * /hotels/1/addons/{id} can never touch another hotel's add-on.
     */
    private Addon resolveWithinHotel(Long hotelId, Long addonId) {
        Addon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Add-on not found with ID: " + addonId));
        if (!addon.getHotel().getId().equals(hotelId)) {
            throw new ResourceNotFoundException(String.format(
                    "Add-on %d does not belong to hotel %d.", addonId, hotelId));
        }
        return addon;
    }

    private AddonResponseDTO toDTO(Addon addon) {
        return new AddonResponseDTO(
                addon.getId(),
                addon.getHotel().getId(),
                addon.getName(),
                addon.getCategory(),
                addon.getPrice(),
                addon.getPriceUnit(),
                Boolean.TRUE.equals(addon.getAvailable()));
    }
}
