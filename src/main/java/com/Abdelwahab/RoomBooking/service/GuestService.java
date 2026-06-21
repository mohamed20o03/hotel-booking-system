package com.Abdelwahab.RoomBooking.service;

import org.springframework.stereotype.Service;
// registerGuest(GuestRequestDTO) → save guest, return response DTO

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;

import org.springframework.transaction.annotation.Transactional;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    
    @Transactional
    public GuestResponseDTO registerGuest(GuestRequestDTO request) {

        if (guestRepository.existsByEmail(request.email())) { 
            throw new DuplicateResourceException("Email is already in use"); 
        }

        Guest guest = Guest.builder()
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .password(request.password()) // Plain text for now; hashing will be added with Spring Security in Phase 4
            .phone(request.phone())
            .nationality(request.nationality())
            .documentType(request.documentType())
            .documentNumber(request.documentNumber())
            .dateOfBirth(request.dateOfBirth())
            .loyaltyTier("STANDARD")
            .createdAt(java.time.LocalDateTime.now())
            .build();

        Guest savedGuest = guestRepository.save(guest);

        return new GuestResponseDTO(
            savedGuest.getId(),
            savedGuest.getFirstName(),
            savedGuest.getLastName(),
            savedGuest.getEmail(),
            savedGuest.getPhone(),
            savedGuest.getLoyaltyTier(),
            savedGuest.getCreatedAt()
        );
    }
    @Transactional(readOnly = true)
    public GuestResponseDTO getGuestById(Long id) {
        Guest guest = guestRepository.findById(id)
            .orElseThrow(() -> new com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException("Guest not found with ID: " + id));

        return new GuestResponseDTO(
            guest.getId(),
            guest.getFirstName(),
            guest.getLastName(),
            guest.getEmail(),
            guest.getPhone(),
            guest.getLoyaltyTier(),
            guest.getCreatedAt()
        );
    }
}