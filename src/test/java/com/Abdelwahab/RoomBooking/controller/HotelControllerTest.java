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

    // GET is permitAll in SecurityConfig — an anonymous caller gets 200.
    @Test
    @WithAnonymousUser
    public void getAllHotels_isPublic() throws Exception {
        when(hotelService.getAllHotels()).thenReturn(List.of(sampleHotel()));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nile Grand"));
    }

    // A missing hotel maps to 404 via GlobalExceptionHandler, even for public reads.
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

    // The write must be blocked for a plain user even though GET on the same path is open.
    @Test
    @WithMockUser(roles = "USER")
    public void createHotel_returns403_forNonAdmin() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).createHotel(any());
    }

    // And blocked entirely for an anonymous caller.
    @Test
    @WithAnonymousUser
    public void createHotel_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).createHotel(any());
    }

    // Bad body (star rating out of range) fails @Valid with 400 before the service.
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
