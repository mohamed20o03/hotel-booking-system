package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;

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

    @Test
    public void getHotelById_WhenHotelExists_ReturnsHotel() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));

        HotelResponseDTO response = hotelService.getHotelById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Grand Hotel");
        verify(hotelRepository, times(1)).findById(1L);
    }

    @Test
    public void getHotelById_WhenHotelNotFound_ThrowsException() {
        when(hotelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getHotelById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: 99");
        
        verify(hotelRepository, times(1)).findById(99L);
    }
}
