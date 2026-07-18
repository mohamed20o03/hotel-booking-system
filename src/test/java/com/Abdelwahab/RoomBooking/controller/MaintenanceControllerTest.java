package com.Abdelwahab.RoomBooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.time.LocalDate;

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

import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockRequestDTO;
import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockResponseDTO;
import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.service.MaintenanceService;

/**
 * HTTP-layer test for MaintenanceController — the REFERENCE pattern for this project.
 *
 * What a controller test proves that a service test cannot: the endpoint's
 * behaviour OVER THE WIRE. Here that means four things a MaintenanceService unit
 * test never touches —
 *   1. RBAC     — a ROLE_USER is rejected (403); only ROLE_ADMIN gets through.
 *   2. Auth     — an anonymous request never reaches the controller.
 *   3. Validation — an invalid @RequestBody is rejected (400) before the service.
 *   4. Error mapping — a domain exception becomes the right HTTP status (409),
 *      via GlobalExceptionHandler.
 * The service itself is mocked (@MockitoBean): its logic is covered in
 * MaintenanceServiceTest. This class only asserts the web contract around it.
 *
 * WHY @SpringBootTest + a hand-built MockMvc rather than @WebMvcTest:
 * the real security chain (SecurityConfig + the JWT filter) needs the full
 * context to wire up. Loading it means the RBAC rules under test are the ACTUAL
 * production rules, not a stand-in. @WithMockUser / @WithAnonymousUser populate
 * the SecurityContext directly, so no real JWT cookie is needed — but only once
 * MockMvc is built with .apply(springSecurity()) (see setUp): without it, Boot
 * 4.1 leaves every request anonymous and all authenticated cases 403.
 *
 * HOW TO REUSE THIS for another controller:
 *   - swap the @MockitoBean'd service and the request/response DTOs,
 *   - keep the four test shapes (authorized-happy-path, wrong-role-403,
 *     invalid-body-400, service-throws-4xx),
 *   - drop the RBAC cases for endpoints that aren't role-gated, and add an
 *     anonymous-allowed case instead for public endpoints.
 * Not every controller needs its own class — cover each DISTINCT pattern once
 * (one role-gated, one public, one validation-heavy) rather than every endpoint.
 */
@SpringBootTest
public class MaintenanceControllerTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    // Replaces the real MaintenanceService bean in the context, so these tests
    // exercise only the controller + security + serialization, not booking logic.
    @MockitoBean private MaintenanceService maintenanceService;

    // Build MockMvc manually with the springSecurity() configurer. Under Boot 4.1's
    // @AutoConfigureMockMvc the @WithMockUser context was NOT propagated into the
    // filter chain (every request arrived anonymous → 403), so we wire the security
    // filter in ourselves. This is the load-bearing line for every RBAC assertion.
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    // Request bodies are inline JSON strings, not serialized DTOs: it keeps the
    // test self-contained (no ObjectMapper/Jackson-module wiring) and lets the
    // validation case send a deliberately malformed body a typed DTO couldn't hold.
    private static final String VALID_BODY = """
            {"roomId":7,"startDate":"2026-12-10","endDate":"2026-12-13","reason":"Repainting"}""";

    private MaintenanceBlockResponseDTO sampleResponse() {
        return new MaintenanceBlockResponseDTO(
                1L, 7L, 101, "Standard Double",
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13), "Repainting");
    }

    // 1. Happy path — an admin with a valid body gets 201 and the created block.
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createBlock_returns201_forAdmin_withValidBody() throws Exception {
        when(maintenanceService.createBlock(any(MaintenanceBlockRequestDTO.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value(101));

        verify(maintenanceService).createBlock(any(MaintenanceBlockRequestDTO.class));
    }

    // 2. RBAC — a plain ROLE_USER is forbidden, and the service is never reached.
    @Test
    @WithMockUser(roles = "USER")
    public void createBlock_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(post("/api/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(maintenanceService, never()).createBlock(any());
    }

    // 3. Auth — an anonymous request is rejected before hitting the controller.
    @Test
    @WithAnonymousUser
    public void createBlock_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(maintenanceService, never()).createBlock(any());
    }

    // 4. Validation — a missing required field fails @Valid with 400, pre-service.
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createBlock_returns400_whenRoomIdMissing() throws Exception {
        // roomId omitted -> @NotNull fails.
        String body = "{\"startDate\":\"2026-12-10\",\"endDate\":\"2026-12-13\",\"reason\":\"x\"}";

        mockMvc.perform(post("/api/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(maintenanceService, never()).createBlock(any());
    }

    // 5. Error mapping — a domain conflict surfaces as 409 via GlobalExceptionHandler.
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createBlock_returns409_whenServiceReportsNoAvailability() throws Exception {
        when(maintenanceService.createBlock(any(MaintenanceBlockRequestDTO.class)))
                .thenThrow(new NoAvailabilityException("all rooms are booked that night"));

        mockMvc.perform(post("/api/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
