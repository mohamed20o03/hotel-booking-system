package com.Abdelwahab.RoomBooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.Abdelwahab.RoomBooking.dto.PaymentResponseDTO;
import com.Abdelwahab.RoomBooking.exception.PaymentException;
import com.Abdelwahab.RoomBooking.service.PaymentService;

/**
 * HTTP-layer test for PaymentController — the AUTHENTICATED-ANY-USER + ERROR-MAPPING
 * pattern.
 *
 * Payment isn't role-gated (any logged-in guest pays their own booking), so there
 * are no ADMIN/USER cases here; the value is in the .anyRequest().authenticated()
 * rule and in how domain failures become HTTP status codes via
 * GlobalExceptionHandler:
 *   - PaymentException        → 409 Conflict   (e.g. overpayment, wrong state)
 *   - IllegalArgumentException → 400 Bad Request (e.g. paying someone else's booking)
 * See MaintenanceControllerTest for the reference explanation of the harness.
 */
@SpringBootTest
public class PaymentControllerTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;

    // See MaintenanceControllerTest.setUp: springSecurity() must be applied or
    // @WithMockUser won't reach the filter chain under Boot 4.1.
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String VALID_BODY = """
            {"reservationId":500,"amount":300.00,"method":"VISA"}""";

    private PaymentResponseDTO sampleResponse() {
        return new PaymentResponseDTO(
                1L, 500L, "AB12CD34", new BigDecimal("300.00"), "EGP", "VISA",
                "SIMULATED", "txn-1", "SUCCESS", "CONFIRMED",
                new BigDecimal("300.00"), BigDecimal.ZERO, LocalDateTime.now());
    }

    // Happy path — an authenticated guest pays and gets 201 with the settled state.
    @Test
    @WithMockUser(username = "john@example.com")
    public void pay_returns201_forAuthenticatedGuest() throws Exception {
        when(paymentService.pay(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.balanceDue").value(0));

        verify(paymentService).pay(any());
    }

    // Auth — no login means no payment; the request is rejected before the service.
    @Test
    @WithAnonymousUser
    public void pay_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).pay(any());
    }

    // Error mapping — a domain payment failure surfaces as 409, not 500.
    @Test
    @WithMockUser(username = "john@example.com")
    public void pay_returns409_whenPaymentRejected() throws Exception {
        when(paymentService.pay(any()))
                .thenThrow(new PaymentException("Payment exceeds the balance due."));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // Error mapping — paying a booking you don't own is an IllegalArgumentException → 400.
    @Test
    @WithMockUser(username = "john@example.com")
    public void pay_returns400_whenNotOwner() throws Exception {
        when(paymentService.pay(any()))
                .thenThrow(new IllegalArgumentException("You do not have permission to pay this reservation."));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // Validation — a non-positive amount fails @DecimalMin with 400 before the service.
    @Test
    @WithMockUser(username = "john@example.com")
    public void pay_returns400_whenAmountNotPositive() throws Exception {
        String badBody = VALID_BODY.replace("\"amount\":300.00", "\"amount\":0");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).pay(any());
    }
}
