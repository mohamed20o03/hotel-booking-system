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
import lombok.extern.slf4j.Slf4j;

/**
 * Manages a hotel's catalogue of optional add-ons (airport transfer, spa, etc.).
 *
 * <p><strong>Responsibility.</strong> Owns the {@link Addon} definitions a hotel
 * offers. These catalogue entries are the source of truth for pricing and
 * availability that {@link ReservationAddonService} freezes onto a booking at
 * attach time.
 *
 * <p><strong>Security &amp; scope.</strong> Reads (browsing available add-ons) are
 * public; writes are admin-only, gated by the {@code /api/hotels/**} rules in
 * {@code SecurityConfig} plus {@code @PreAuthorize} on the controller. Every write
 * is scoped to the hotel in the path, so one hotel's add-on can never be mutated
 * through another hotel's URL.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repositories; safe for concurrent request threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddonService {

    private final AddonRepository addonRepository;
    private final HotelRepository hotelRepository;

    /**
     * Lists the add-ons a guest can actually book right now for the given hotel —
     * available entries only.
     *
     * <p>Read-only transactional: a pure query, marked {@code readOnly} to skip
     * dirty-checking overhead.
     *
     * @param hotelId the hotel whose catalogue to browse; must identify an existing
     *                hotel.
     * @return the available add-ons as {@link AddonResponseDTO} views; an empty list
     *         if none are available.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
    @Transactional(readOnly = true)
    public List<AddonResponseDTO> getAvailableAddons(Long hotelId) {
        requireHotel(hotelId);
        return addonRepository.findByHotelIdAndAvailableTrue(hotelId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Creates a catalogue add-on under the given hotel.
     *
     * <p>Defaults availability to {@code true} when the request leaves it unset, so
     * a new add-on is sellable unless explicitly withheld. Read-write transactional:
     * the insert commits atomically.
     *
     * @param hotelId the owning hotel; must identify an existing hotel.
     * @param request the add-on's attributes (name, category, price, price unit, and
     *                optional availability flag); must be non-{@code null} and valid.
     * @return an {@link AddonResponseDTO} view of the persisted add-on, including its
     *         generated id.
     * @throws ResourceNotFoundException if no hotel exists with the given id.
     */
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
        Addon saved = addonRepository.save(addon);
        log.info("Add-on created [addonId={} hotelId={} name={}]",
                saved.getId(), hotelId, saved.getName());
        return toDTO(saved);
    }

    /**
     * Updates a catalogue add-on in place, scoped to its owning hotel.
     *
     * <p>The availability flag is only changed when the request supplies one, so a
     * partial update never silently withdraws an add-on. Read-write transactional:
     * the resolved entity is mutated and flushed on commit via JPA dirty-checking.
     *
     * <p>Note this rewrites the catalogue price only; add-on lines already frozen
     * onto existing reservations are unaffected.
     *
     * @param hotelId the hotel the add-on must belong to.
     * @param addonId the add-on to update.
     * @param request the replacement attribute values; must be non-{@code null} and
     *                valid. A {@code null} availability leaves the current flag intact.
     * @return an {@link AddonResponseDTO} view of the updated add-on.
     * @throws ResourceNotFoundException if the add-on does not exist or does not
     *         belong to the given hotel; thrown before any mutation.
     */
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
        log.info("Add-on updated [addonId={} hotelId={}]", addonId, hotelId);
        return toDTO(addonRepository.save(addon));
    }

    /**
     * Deletes a catalogue add-on, scoped to its owning hotel.
     *
     * <p>Read-write transactional. An add-on still referenced by a booking cannot be
     * removed: a {@code reservation_addon} foreign key with {@code ON DELETE RESTRICT}
     * blocks the delete at the database layer, which is the final guard against
     * orphaning a booked line.
     *
     * @param hotelId the hotel the add-on must belong to.
     * @param addonId the add-on to delete.
     * @throws ResourceNotFoundException if the add-on does not exist or does not
     *         belong to the given hotel.
     */
    @Transactional
    public void deleteAddon(Long hotelId, Long addonId) {
        Addon addon = resolveWithinHotel(hotelId, addonId);
        // A reservation_addon FK with ON DELETE RESTRICT blocks deletion of an
        // add-on still referenced by a booking — the DB is the final guard.
        addonRepository.delete(addon);
        log.info("Add-on deleted [addonId={} hotelId={}]", addonId, hotelId);
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
