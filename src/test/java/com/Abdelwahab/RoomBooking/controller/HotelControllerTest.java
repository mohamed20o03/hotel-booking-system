package com.Abdelwahab.RoomBooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.service.HotelService;

/**
 * HTTP-layer test for HotelController — the PUBLIC-READ / ADMIN-WRITE pattern.
 *
 * Distinct from MaintenanceControllerTest (which is admin-only end to end): here
 * the same base path is public for GET and admin-gated for the mutating verbs, so
 * the interesting assertions are (a) an anonymous user CAN read but CANNOT write,
 * and (b) the write rule is enforced by HTTP verb, not just path. See
 * MaintenanceControllerTest for the reference explanation of the harness itself.
 */
@SpringBootTest
public class HotelControllerTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;
    @MockitoBean private HotelService hotelService;

    // See MaintenanceControllerTest.setUp: springSecurity() must be applied or
    // @WithMockUser won't reach the filter chain under Boot 4.1.
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String VALID_BODY = """
            {"name":"Nile Grand","address":"1 Corniche","city":"Cairo","country":"Egypt",
             "phone":"+20 2 1234","email":"stay@nilegrand.example","starRating":5,
             "timezone":"Africa/Cairo"}""";

    private HotelResponseDTO sampleHotel() {
        return new HotelResponseDTO(
                1L, "Nile Grand", "Cairo", "Egypt", 5,
                "+20 2 1234", "stay@nilegrand.example", "Africa/Cairo");
    }

    // ── Public reads ────────────────────────────────────────────────

    /**
     * Given the service returns a hotel list and the GET route is permitAll in SecurityConfig;
     * when an anonymous caller GETs /api/hotels; then the response is 200 OK with the
     * serialized list, proving the read path requires no authentication.
     */
    @Test
    @WithAnonymousUser
    public void getAllHotels_isPublic() throws Exception {
        when(hotelService.getAllHotels()).thenReturn(List.of(sampleHotel()));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nile Grand"));
    }

    /**
     * Given the service raises ResourceNotFoundException for an unknown id;
     * when an anonymous caller GETs /api/hotels/99; then GlobalExceptionHandler maps the
     * exception to 404 Not Found with a body status of 404 — error mapping applies to
     * public reads exactly as it does to authenticated routes.
     */
    @Test
    @WithAnonymousUser
    public void getHotelById_returns404_whenMissing() throws Exception {
        when(hotelService.getHotelById(99L))
                .thenThrow(new ResourceNotFoundException("Hotel not found with ID: 99"));

        mockMvc.perform(get("/api/hotels/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Admin-gated writes ──────────────────────────────────────────

    /**
     * Given an authenticated ROLE_ADMIN and the service returning the created hotel;
     * when the admin POSTs a valid body to /api/hotels; then the response is 201 Created
     * with the persisted id and the service is invoked — the admin-gated write succeeds.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createHotel_returns201_forAdmin() throws Exception {
        when(hotelService.createHotel(any())).thenReturn(sampleHotel());

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(hotelService).createHotel(any());
    }

    /**
     * Given an authenticated but non-admin ROLE_USER;
     * when they POST a valid body to /api/hotels; then the response is 403 Forbidden and
     * the service is never reached — the write rule is enforced by HTTP verb, so a role
     * that may GET the same path is still blocked from mutating it.
     */
    @Test
    @WithMockUser(roles = "USER")
    public void createHotel_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).createHotel(any());
    }

    /**
     * Given no authenticated identity;
     * when an anonymous caller POSTs a valid body to /api/hotels; then the response is
     * 403 Forbidden (not 401) and the service is never reached — the write is closed to
     * anonymous callers just as it is to non-admins.
     */
    @Test
    @WithAnonymousUser
    public void createHotel_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).createHotel(any());
    }

    /**
     * Given an authenticated ROLE_ADMIN and a body whose starRating (9) exceeds the
     * allowed range; when the admin POSTs it to /api/hotels; then @Valid rejects the
     * request with 400 Bad Request before the service is called — validation runs after
     * the RBAC check but ahead of the controller body.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createHotel_returns400_whenStarRatingOutOfRange() throws Exception {
        String badBody = VALID_BODY.replace("\"starRating\":5", "\"starRating\":9");

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());

        verify(hotelService, never()).createHotel(any());
    }
}
