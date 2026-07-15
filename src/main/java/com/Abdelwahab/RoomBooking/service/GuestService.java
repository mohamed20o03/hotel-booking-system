    package com.Abdelwahab.RoomBooking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public GuestResponseDTO registerGuest(GuestRequestDTO request) {

        if (guestRepository.existsByEmail(request.email())) { 
            throw new DuplicateResourceException("Email is already in use"); 
        }

        Guest guest = Guest.builder()
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .phone(request.phone())
            .nationality(request.nationality())
            .documentType(request.documentType())
            .documentNumber(request.documentNumber())
            .dateOfBirth(request.dateOfBirth())
            .loyaltyTier("STANDARD")
            .role("ROLE_USER") // self-registration never mints an admin
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