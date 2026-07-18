package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Abdelwahab.RoomBooking.dto.AddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.AddonResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Addon;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.repository.AddonRepository;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;

/**
 * Unit test for AddonService — catalogue CRUD scoped to a hotel. Verifies the
 * available-only read filter, the create default for availability, and the
 * cross-hotel guard that stops one hotel's URL mutating another's add-on.
 */
@ExtendWith(MockitoExtension.class)
public class AddonServiceTest {

    @Mock private AddonRepository addonRepository;
    @Mock private HotelRepository hotelRepository;
    @InjectMocks private AddonService addonService;

    private Hotel hotel;
    private Addon addon;

    @BeforeEach
    public void setup() {
        hotel = Hotel.builder().id(1L).name("Nile Grand").build();
        addon = Addon.builder()
                .id(10L)
                .hotel(hotel)
                .name("Airport Transfer")
                .category("TRANSPORTATION")
                .price(new BigDecimal("50.00"))
                .priceUnit("FLAT_RATE")
                .available(true)
                .build();
    }

    /**
     * Given the hotel exists and the repository returns one available add-on;
     * when the available catalogue is requested; then exactly that add-on is returned
     * with its available flag set — the read filter surfaces only available rows.
     */
    @Test
    public void getAvailableAddons_returnsOnlyAvailable_forExistingHotel() {
        when(hotelRepository.existsById(1L)).thenReturn(true);
        when(addonRepository.findByHotelIdAndAvailableTrue(1L)).thenReturn(List.of(addon));

        List<AddonResponseDTO> result = addonService.getAvailableAddons(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).available()).isTrue();
    }

    /**
     * Given the hotel does not exist;
     * when the available catalogue is requested for it; then a ResourceNotFoundException
     * naming the id is raised and the add-on repository is never queried — the parent
     * hotel is validated first.
     */
    @Test
    public void getAvailableAddons_throws_whenHotelMissing() {
        when(hotelRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> addonService.getAvailableAddons(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with ID: 99");

        verify(addonRepository, never()).findByHotelIdAndAvailableTrue(any());
    }

    /**
     * Given the hotel exists and a create request omits the availability flag (null);
     * when the add-on is created; then the captured persisted entity has available
     * defaulted to true.
     */
    @Test
    public void createAddon_defaultsAvailableToTrue_whenOmitted() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(addonRepository.save(any(Addon.class))).thenAnswer(i -> i.getArguments()[0]);

        addonService.createAddon(1L, new AddonRequestDTO(
                "Spa", "SPA", new BigDecimal("80.00"), "PER_PERSON", null));

        ArgumentCaptor<Addon> captor = ArgumentCaptor.forClass(Addon.class);
        verify(addonRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isTrue();
    }

    /**
     * Given the target add-on belongs to hotel 2 but the update is scoped to hotel 1;
     * when the update is attempted; then a ResourceNotFoundException reporting the
     * cross-hotel mismatch is raised and no save occurs — the cross-hotel guard stops
     * one hotel from mutating another's add-on.
     */
    @Test
    public void updateAddon_throws_whenAddonBelongsToAnotherHotel() {
        Hotel other = Hotel.builder().id(2L).build();
        addon.setHotel(other);
        when(addonRepository.findById(10L)).thenReturn(Optional.of(addon));

        assertThatThrownBy(() -> addonService.updateAddon(1L, 10L, new AddonRequestDTO(
                "X", "SPA", new BigDecimal("1.00"), "FLAT_RATE", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to hotel 1");

        verify(addonRepository, never()).save(any(Addon.class));
    }

    /**
     * Given the add-on exists and is scoped to the requesting hotel;
     * when it is deleted; then the repository delete is invoked for that add-on.
     */
    @Test
    public void deleteAddon_removesAddon_whenScopedToHotel() {
        when(addonRepository.findById(10L)).thenReturn(Optional.of(addon));

        addonService.deleteAddon(1L, 10L);

        verify(addonRepository).delete(addon);
    }
}
