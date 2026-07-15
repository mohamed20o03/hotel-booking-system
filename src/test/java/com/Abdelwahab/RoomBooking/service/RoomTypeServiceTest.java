package com.Abdelwahab.RoomBooking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.Abdelwahab.RoomBooking.dto.RoomTypeRequestDTO;
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

    @Test
    public void createRoomType_attachesHotel_andPersists() {
        Long hotelId = 1L;
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(Hotel.builder().id(hotelId).build()));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> {
            RoomType rt = inv.getArgument(0);
            rt.setId(1L);
            return rt;
        });

        RoomTypeRequestDTO request = new RoomTypeRequestDTO(
            "Deluxe Room", "A spacious deluxe room", 2, 10,
            new java.math.BigDecimal("150.00"), "EGP");

        RoomTypeResponseDTO response = roomTypeService.createRoomType(hotelId, request);

        assertThat(response.name()).isEqualTo("Deluxe Room");

        ArgumentCaptor<RoomType> captor = ArgumentCaptor.forClass(RoomType.class);
        verify(roomTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getHotel().getId()).isEqualTo(hotelId);
    }

    @Test
    public void createRoomType_WhenHotelNotFound_ThrowsException() {
        Long hotelId = 99L;
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        RoomTypeRequestDTO request = new RoomTypeRequestDTO(
            "Deluxe Room", "desc", 2, 10, new java.math.BigDecimal("150.00"), "EGP");

        assertThatThrownBy(() -> roomTypeService.createRoomType(hotelId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hotel not found with ID: " + hotelId);

        verify(roomTypeRepository, times(0)).save(any());
    }

    @Test
    public void updateRoomType_WhenBelongsToDifferentHotel_ThrowsException() {
        // sampleRoomType belongs to hotel 1; caller targets hotel 2's path.
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));

        RoomTypeRequestDTO request = new RoomTypeRequestDTO(
            "Renamed", "desc", 2, 10, new java.math.BigDecimal("150.00"), "EGP");

        assertThatThrownBy(() -> roomTypeService.updateRoomType(2L, 1L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("does not belong to hotel 2");

        verify(roomTypeRepository, times(0)).save(any());
    }

    @Test
    public void deleteRoomType_WhenBelongsToDifferentHotel_ThrowsException() {
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(sampleRoomType));

        assertThatThrownBy(() -> roomTypeService.deleteRoomType(2L, 1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("does not belong to hotel 2");

        verify(roomTypeRepository, times(0)).delete(any());
    }
}
