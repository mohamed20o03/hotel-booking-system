package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Abdelwahab.RoomBooking.dto.HotelRequestDTO;
import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;

/**
 * Plain Mockito unit test for HotelService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); the HotelRepository collaborator is a @Mock
 * injected into the service). It covers the hotel CRUD contract in isolation from
 * persistence: list and single-item reads with their entity-to-DTO mapping, the
 * not-found path raising ResourceNotFoundException, create/update round-trips, and the
 * guard that update and delete never touch the repository for a missing hotel.
 */
@ExtendWith(MockitoExtension.class)
public class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private HotelService hotelService;

    private Hotel sampleHotel;

    @BeforeEach
    public void setup() {
        sampleHotel = Hotel.builder()
                .id(1L)
                .name("Grand Hotel")
                .address("123 Main St")
                .city("Cairo")
                .country("Egypt")
                .phone("+2012345678")
                .email("contact@grandhotel.com")
                .starRating(5)
                .timezone("Africa/Cairo")
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Given the repository returns one hotel;
     * when all hotels are listed; then a single mapped DTO with the expected id and name
     * is returned and findAll is queried once.
     */
    @Test
    public void getAllHotels_ReturnsListOfHotels() {
        when(hotelRepository.findAll()).thenReturn(List.of(sampleHotel));

        List<HotelResponseDTO> responses = hotelService.getAllHotels();

        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("Grand Hotel");
        verify(hotelRepository, times(1)).findAll();
    }

    /**
     * Given the repository finds the hotel;
     * when it is looked up by id; then the mapped DTO carries the expected id and name.
     */
    @Test
    public void getHotelById_WhenHotelExists_ReturnsHotel() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));

        HotelResponseDTO response = hotelService.getHotelById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Grand Hotel");
        verify(hotelRepository, times(1)).findById(1L);
    }

    /**
     * Given the repository returns empty for an unknown id;
     * when it is looked up; then a ResourceNotFoundException naming the id is raised.
     */
    @Test
    public void getHotelById_WhenHotelNotFound_ThrowsException() {
        when(hotelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getHotelById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: 99");

        verify(hotelRepository, times(1)).findById(99L);
    }

    /**
     * Given the repository assigns an id on save;
     * when a hotel is created from a request DTO; then the returned DTO carries the
     * persisted id and name and save is invoked once.
     */
    @Test
    public void createHotel_persistsAndReturnsDTO() {
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(inv -> {
            Hotel h = inv.getArgument(0);
            h.setId(1L);
            return h;
        });

        HotelRequestDTO request = new HotelRequestDTO(
            "Grand Hotel", "123 Main St", "Cairo", "Egypt",
            "+2012345678", "contact@grandhotel.com", 5, "Africa/Cairo");

        HotelResponseDTO response = hotelService.createHotel(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Grand Hotel");
        verify(hotelRepository, times(1)).save(any(Hotel.class));
    }

    /**
     * Given the repository finds the hotel and echoes the saved entity;
     * when it is updated with new name, city and star rating; then the returned DTO
     * reflects all three changes.
     */
    @Test
    public void updateHotel_appliesChanges() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

        HotelRequestDTO request = new HotelRequestDTO(
            "Renamed Hotel", "123 Main St", "Alexandria", "Egypt",
            "+2012345678", "contact@grandhotel.com", 4, "Africa/Cairo");

        HotelResponseDTO response = hotelService.updateHotel(1L, request);

        assertThat(response.name()).isEqualTo("Renamed Hotel");
        assertThat(response.city()).isEqualTo("Alexandria");
        assertThat(response.starRating()).isEqualTo(4);
    }

    /**
     * Given the repository returns empty for an unknown id;
     * when an update is attempted; then a ResourceNotFoundException is raised and save is
     * never called — the existence check gates the write.
     */
    @Test
    public void updateHotel_WhenHotelNotFound_ThrowsException() {
        when(hotelRepository.findById(99L)).thenReturn(Optional.empty());

        HotelRequestDTO request = new HotelRequestDTO(
            "X", "Y", "Z", "Egypt", "+2012345678", "x@x.com", 3, null);

        assertThatThrownBy(() -> hotelService.updateHotel(99L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: 99");

        verify(hotelRepository, times(0)).save(any());
    }

    /**
     * Given the repository reports the id does not exist;
     * when a delete is attempted; then a ResourceNotFoundException is raised and
     * deleteById is never called.
     */
    @Test
    public void deleteHotel_WhenHotelNotFound_ThrowsException() {
        when(hotelRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> hotelService.deleteHotel(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: 99");

        verify(hotelRepository, times(0)).deleteById(any());
    }
}
