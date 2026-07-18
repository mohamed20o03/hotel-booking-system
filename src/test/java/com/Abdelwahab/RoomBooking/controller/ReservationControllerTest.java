package com.Abdelwahab.RoomBooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.service.ReservationAddonService;
import com.Abdelwahab.RoomBooking.service.ReservationService;

/**
 * HTTP-layer test for ReservationController.
 *
 * This controller is the reason a per-controller test earns its keep: it mixes
 * access patterns on ONE controller in ways the other controller tests don't —
 *   1. Authenticated-any-user reads — a plain ROLE_USER reaches /my-reservations;
 *      an anonymous request does not (the anyRequest().authenticated() default).
 *   2. Mixed gating — PATCH /{id}/check-in is admin-only (SecurityConfig URL rule
 *      + @PreAuthorize on the method), while every sibling endpoint is open to any
 *      authenticated user. A ROLE_USER must be 403 on check-in but 2xx elsewhere.
 *   3. Ownership + lifecycle error mapping — cancel/check-in enforce row-level
 *      ownership and status rules in the service; those surface as the right HTTP
 *      status via GlobalExceptionHandler (IllegalArgumentException -> 400,
 *      ResourceNotFoundException -> 404) without a role being involved.
 *
 * Setup mirrors MaintenanceControllerTest (the reference): @SpringBootTest with a
 * hand-built MockMvc + .apply(springSecurity()) so @WithMockUser reaches the filter
 * chain under Boot 4.1. Both collaborating services are mocked — their logic lives
 * in ReservationServiceTest / ReservationAddonServiceTest; this class only asserts
 * the web contract (auth, RBAC, validation, status mapping) around them.
 */
@SpringBootTest
public class ReservationControllerTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @MockitoBean private ReservationService reservationService;
    // ReservationController also depends on this; it must be a bean in the context
    // even though the add-on endpoints aren't the focus here.
    @MockitoBean private ReservationAddonService reservationAddonService;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ReservationResponseDTO sampleReservation(String status) {
        return new ReservationResponseDTO(
                1L, "AB12CD34", status,
                42L, "Jane Guest",
                "Standard Double", 101L,
                "Flexible", "EGP",
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13),
                2, 3, new BigDecimal("300.00"),
                LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    // ── 1. Authenticated-any-user read ────────────────────────────────

    /**
     * Given an authenticated ROLE_USER and the service returning their reservations;
     * when they GET /api/reservations/my-reservations (no role gate); then the response
     * is 200 OK with their list — any authenticated user may read their own reservations.
     */
    // A plain ROLE_USER reaches /my-reservations (no role gate) and gets their list.
    @Test
    @WithMockUser(roles = "USER")
    public void getMyReservations_returnsList_forAuthenticatedUser() throws Exception {
        when(reservationService.getMyReservations())
                .thenReturn(List.of(sampleReservation("CONFIRMED")));

        mockMvc.perform(get("/api/reservations/my-reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].confirmationNumber").value("AB12CD34"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        verify(reservationService).getMyReservations();
    }

    /**
     * Given no authenticated identity;
     * when an anonymous caller GETs /api/reservations/my-reservations; then the
     * anyRequest().authenticated() rule rejects it with 403 Forbidden (not 401) and the
     * service is never reached.
     */
    // The anyRequest().authenticated() default: no identity -> never reaches the controller.
    @Test
    @WithAnonymousUser
    public void getMyReservations_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/reservations/my-reservations"))
                .andExpect(status().isForbidden());

        verify(reservationService, never()).getMyReservations();
    }

    // ── 2. Mixed gating: check-in is admin-only among authenticated endpoints ──

    /**
     * Given an authenticated but non-admin ROLE_USER;
     * when they PATCH /api/reservations/1/check-in; then the response is 403 Forbidden and
     * the service is never touched — check-in is admin-only even though sibling endpoints
     * are open to any authenticated user.
     */
    // A ROLE_USER is authenticated but not staff, so the admin-only check-in is 403
    // and the service is never touched.
    @Test
    @WithMockUser(roles = "USER")
    public void checkIn_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/check-in"))
                .andExpect(status().isForbidden());

        verify(reservationService, never()).checkIn(anyLong());
    }

    /**
     * Given an authenticated ROLE_ADMIN and the service returning the checked-in
     * reservation; when the admin PATCHes /api/reservations/1/check-in; then the response
     * is 200 OK reporting CHECKED_IN with the assigned room id, and the service is invoked
     * — the same endpoint the ROLE_USER was forbidden on succeeds for staff.
     */
    // Same endpoint, ROLE_ADMIN: the front-desk action goes through and returns the
    // updated reservation now assigned a room and CHECKED_IN.
    @Test
    @WithMockUser(roles = "ADMIN")
    public void checkIn_returns200_forAdmin() throws Exception {
        when(reservationService.checkIn(1L)).thenReturn(sampleReservation("CHECKED_IN"));

        mockMvc.perform(patch("/api/reservations/1/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.assignedRoomId").value(101));

        verify(reservationService).checkIn(1L);
    }

    // ── 3. Ownership + lifecycle error mapping (no role involved) ──────

    /**
     * Given an authenticated ROLE_USER and the service raising IllegalArgumentException
     * because the caller is not the reservation's owner; when they PATCH
     * /api/reservations/1/cancel; then GlobalExceptionHandler maps it to 400 Bad Request
     * with a body status of 400, and the service was reached — a row-level authorization
     * decision, not a route-level 403.
     */
    // Cancelling a reservation you don't own is a service-level ownership failure
    // (IllegalArgumentException) that surfaces as 400 — the request is authenticated
    // and the endpoint is open, so this is an authorization decision on the row,
    // not the route.
    @Test
    @WithMockUser(roles = "USER")
    public void cancel_returns400_whenCallerIsNotOwner() throws Exception {
        doThrowOnCancel(new IllegalArgumentException(
                "You do not have permission to cancel this reservation."));

        mockMvc.perform(patch("/api/reservations/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(reservationService).cancelReservation(1L);
    }

    /**
     * Given an authenticated ROLE_USER and the service raising ResourceNotFoundException
     * for a missing reservation; when they PATCH /api/reservations/999/cancel; then
     * GlobalExceptionHandler maps it to 404 Not Found with a body status of 404, and the
     * service was reached.
     */
    // Cancelling a reservation that doesn't exist maps to 404 via the handler.
    @Test
    @WithMockUser(roles = "USER")
    public void cancel_returns404_whenReservationMissing() throws Exception {
        doThrowOnCancel(new ResourceNotFoundException("Reservation not found with ID: 999"));

        mockMvc.perform(patch("/api/reservations/999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(reservationService).cancelReservation(999L);
    }

    /**
     * Given an authenticated ROLE_USER who owns the reservation;
     * when they PATCH /api/reservations/1/cancel; then the response is 204 No Content and
     * the service's cancel is invoked — the happy path for an open, authenticated endpoint.
     */
    // Happy-path cancel: an authenticated owner gets 204 No Content.
    @Test
    @WithMockUser(roles = "USER")
    public void cancel_returns204_onSuccess() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/cancel"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelReservation(1L);
    }

    // ── 4. Validation — bad create body is rejected before the service ─

    /**
     * Given an authenticated ROLE_USER and a create body with ratePlanId omitted;
     * when it is POSTed to /api/reservations; then @NotNull fails and @Valid returns 400
     * Bad Request before the service is called.
     */
    // ratePlanId omitted -> @NotNull fails; @Valid returns 400 pre-service.
    @Test
    @WithMockUser(roles = "USER")
    public void createBooking_returns400_whenRatePlanIdMissing() throws Exception {
        String body = """
                {"checkInDate":"2026-12-10","checkOutDate":"2026-12-13","numGuests":2}""";

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(reservationService, never()).createBooking(any());
    }

    // cancelReservation returns void, so it needs Mockito's doThrow form.
    private void doThrowOnCancel(RuntimeException ex) {
        doThrow(ex).when(reservationService).cancelReservation(eq(1L));
        doThrow(ex).when(reservationService).cancelReservation(eq(999L));
    }
}
