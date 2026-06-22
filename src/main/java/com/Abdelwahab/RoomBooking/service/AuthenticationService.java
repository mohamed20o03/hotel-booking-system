package com.Abdelwahab.RoomBooking.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final GuestService guestService;
    private final GuestRepository guestRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public String register(GuestRequestDTO request) {
        guestService.registerGuest(request);
        // After registration, fetch the guest to generate token
        Guest guest = guestRepository.findByEmail(request.email())
            .orElseThrow(() -> new RuntimeException("User not found after registration"));
        
        String jwtToken = jwtService.generateTokens(guest);
        return jwtToken;
    }

    public String login(LoginRequestDTO request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
            )
        );

        Guest guest = guestRepository.findByEmail(request.email())
            .orElseThrow(); // Should exist since authentication passed
            
        String jwtToken = jwtService.generateTokens(guest);
        return jwtToken;
    }
}
