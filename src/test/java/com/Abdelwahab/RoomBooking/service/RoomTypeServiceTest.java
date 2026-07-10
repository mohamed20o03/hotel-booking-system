package com.Abdelwahab.RoomBooking.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.HotelRepository;
import com.Abdelwahab.RoomBooking.repository.RoomTypeRepository;

@ExtendWith(MockitoExtension.class)
public class RoomTypeServiceTest {
    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private RoomTypeService roomTypeService;

    private RoomTypeResponseDTO expectedResponse;
    private RoomType sampleRoomType;

    @BeforeEach
    public void setup(){
        Hotel hotel = Hotel.builder()
            .id(1L)
            .build();

        sampleRoomType = RoomType.builder()
            .id(1L)
            .hotel(hotel)
            .name("Deluxe Room")
            .description("A spacious deluxe room")
            .maxOccupancy(2)
            .totalRooms(10)
            .basePricePerNight(new java.math.BigDecimal("150.00"))
            .currency("EGP")
            .build();

        expectedResponse = new RoomTypeResponseDTO(
            1L,
            "Deluxe Room",
            "A spacious deluxe room",
            2,
            10,
            new java.math.BigDecimal("150.00"),
            "EGP"
        );
    }

    @Test
    public void getRoomTypesByHotel_success() {
        Long hotelId = 1L;
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByHotelId(hotelId)).thenReturn(List.of(sampleRoomType));

        List<RoomTypeResponseDTO> responseDTOs = roomTypeService.getRoomTypesByHotel(hotelId);

        assertThat(responseDTOs).isNotNull();
        assertThat(responseDTOs).hasSize(1);
        assertThat(responseDTOs.get(0).id()).isEqualTo(1L);
        assertThat(responseDTOs.get(0).name()).isEqualTo(expectedResponse.name());

        verify(hotelRepository, times(1)).existsById(hotelId);
        verify(roomTypeRepository, times(1)).findByHotelId(hotelId);
    }

    @Test
    public void getRoomTypesByHotel_WhenHotelNotFound_ThrowsException() {
        Long hotelId = 99L;
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.getRoomTypesByHotel(hotelId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: " + hotelId);

        verify(hotelRepository, times(1)).existsById(hotelId);
        verify(roomTypeRepository, times(0)).findByHotelId(hotelId);
    }
}
