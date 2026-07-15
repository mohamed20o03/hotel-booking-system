package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.PaymentRequestDTO;
import com.Abdelwahab.RoomBooking.dto.PaymentResponseDTO;
import com.Abdelwahab.RoomBooking.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // POST /api/payments
    // Records a payment against the authenticated guest's PENDING reservation.
    // Confirms the booking once the balance is fully settled.
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> pay(@Valid @RequestBody PaymentRequestDTO request) {
        PaymentResponseDTO response = paymentService.pay(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
