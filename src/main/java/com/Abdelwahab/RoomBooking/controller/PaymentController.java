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

/**
 * HTTP entry point for settling reservations: recording a payment against a booking.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates the request, delegates to {@link PaymentService}, and maps the result to
 * an HTTP status code. It holds no business logic: ownership, hold-expiry, balance,
 * and the confirmation flip all live in the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link PaymentService}; each request runs on its
 * own thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> The {@code /api/payments} path matches no
 * explicit rule in {@code SecurityConfig}, so it falls to the
 * {@code anyRequest().authenticated()} default: any logged-in caller, regardless of
 * role, may pay. The payment is <strong>ownership-scoped</strong> in the service —
 * the caller is resolved from the security context and must own the reservation, so
 * a guest cannot pay for someone else's booking by guessing an id. Because there is
 * no {@code AuthenticationEntryPoint}, an unauthenticated request yields
 * {@code 403 Forbidden}.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404},
 * {@code PaymentException} (reservation not awaiting payment, hold window elapsed, or
 * amount exceeding the balance due) {@code → 409}, an ownership violation raised as
 * {@code IllegalArgumentException → 400}, and bean-validation failures on
 * {@code @Valid → 400}.
 *
 * @see PaymentService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Records a payment against the authenticated guest's {@code PENDING}
     * reservation, confirming the booking once the balance is fully settled.
     *
     * <p>Requires authentication; the payment is attributed to the caller and applies
     * only to a reservation they own. Partial payments leave the reservation
     * {@code PENDING} with a balance due; the payment that clears the balance flips it
     * to {@code CONFIRMED} and drops the hold expiry. The request body is validated
     * with {@code @Valid} before the service is reached.
     *
     * @param request the payment to apply — target reservation id, amount, and
     *                method; validated by DTO constraints.
     * @return {@code 201 Created} with the {@link PaymentResponseDTO} recording the
     *         captured payment, the running paid total, and the remaining balance.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         reservation has the requested id (mapped to {@code 404}).
     * @throws IllegalArgumentException if the caller does not own the reservation
     *         (mapped to {@code 400}).
     * @throws com.Abdelwahab.RoomBooking.exception.PaymentException if the reservation
     *         is not awaiting payment, its hold window has elapsed, or the amount
     *         exceeds the balance due (mapped to {@code 409}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> pay(@Valid @RequestBody PaymentRequestDTO request) {
        PaymentResponseDTO response = paymentService.pay(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
