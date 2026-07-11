package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.RatePlanRate;
import com.Abdelwahab.RoomBooking.model.RateSource;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.RatePlanRateRepository;

@ExtendWith(MockitoExtension.class)
public class PricingServiceTest {

    @Mock private RatePlanRateRepository ratePlanRateRepository;
    @InjectMocks private PricingService pricingService;

    private RoomType roomType;
    private RatePlan ratePlan;

    @BeforeEach
    public void setup() {
        roomType = RoomType.builder()
                .id(1L)
                .name("Standard Double")
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("EGP")
                .maxOccupancy(2)
                .build();

        ratePlan = RatePlan.builder()
                .id(10L)
                .roomType(roomType)
                .name("Non-Refundable")
                .minStayNights(1)
                .isRefundable(false)
                .build();
    }

    private RatePlanRate override(LocalDate date, String price) {
        return RatePlanRate.builder()
                .ratePlan(ratePlan)
                .date(date)
                .price(new BigDecimal(price))
                .build();
    }

    @Test
    public void quote_allBaseRate_whenNoOverrides() {
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 4); // 3 nights
        when(ratePlanRateRepository.findForStay(10L, checkIn, checkOut)).thenReturn(List.of());

        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        assertThat(quote.nightCount()).isEqualTo(3);
        assertThat(quote.currency()).isEqualTo("EGP");
        assertThat(quote.total()).isEqualByComparingTo("300.00"); // 3 x 100
        assertThat(quote.nights()).allMatch(n -> n.source() == RateSource.BASE);
    }

    @Test
    public void quote_checkoutNightIsNotCharged_halfOpenInterval() {
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 2); // exactly 1 night
        when(ratePlanRateRepository.findForStay(10L, checkIn, checkOut)).thenReturn(List.of());

        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        assertThat(quote.nightCount()).isEqualTo(1);
        assertThat(quote.nights().get(0).date()).isEqualTo(checkIn);
        assertThat(quote.total()).isEqualByComparingTo("100.00");
    }

    @Test
    public void quote_mixesOverrideAndBase_dayByDay() {
        // The worked example, scaled down: base 100, promo 80 for Jun 5–7.
        // Stay Jun 3 -> Jun 9 (checkout) = 6 nights:
        //   Jun 3,4     -> 100 BASE  = 200
        //   Jun 5,6,7   ->  80 PLAN  = 240
        //   Jun 8       -> 100 BASE  = 100
        //   Jun 9 is checkout, not charged.
        //   total = 540
        LocalDate checkIn = LocalDate.of(2026, 6, 3);
        LocalDate checkOut = LocalDate.of(2026, 6, 9);
        when(ratePlanRateRepository.findForStay(10L, checkIn, checkOut)).thenReturn(List.of(
                override(LocalDate.of(2026, 6, 5), "80.00"),
                override(LocalDate.of(2026, 6, 6), "80.00"),
                override(LocalDate.of(2026, 6, 7), "80.00")));

        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        assertThat(quote.nightCount()).isEqualTo(6);
        assertThat(quote.total()).isEqualByComparingTo("540.00");

        // Verify the per-night source tagging
        assertThat(quote.nights()).extracting(n -> n.source()).containsExactly(
                RateSource.BASE, RateSource.BASE,
                RateSource.PLAN, RateSource.PLAN, RateSource.PLAN,
                RateSource.BASE);
    }

    @Test
    public void quote_overridesOutsideStayAreIgnoredByQuery() {
        // The repository is asked only for the stay window, so anything it returns
        // is applied. This test documents that quote trusts findForStay's filtering.
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 3); // 2 nights
        when(ratePlanRateRepository.findForStay(10L, checkIn, checkOut)).thenReturn(List.of(
                override(LocalDate.of(2026, 6, 1), "70.00")));

        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        assertThat(quote.total()).isEqualByComparingTo("170.00"); // 70 + 100
        assertThat(quote.nights().get(0).source()).isEqualTo(RateSource.PLAN);
        assertThat(quote.nights().get(1).source()).isEqualTo(RateSource.BASE);
    }

    @Test
    public void quote_rejectsRatePlanFromDifferentRoomType() {
        RoomType otherType = RoomType.builder().id(999L).name("Suite").build();
        RatePlan foreignPlan = RatePlan.builder()
                .id(20L).roomType(otherType).name("Foreign").build();

        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 3);

        assertThatThrownBy(() -> pricingService.quote(roomType, foreignPlan, checkIn, checkOut))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to room type");

        // Must fail fast, before touching the database
        verify(ratePlanRateRepository, never()).findForStay(any(), any(), any());
    }

    @Test
    public void quote_currencyComesFromRoomType() {
        roomType.setCurrency("USD");
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 2);
        when(ratePlanRateRepository.findForStay(eq(10L), any(), any())).thenReturn(List.of());

        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        assertThat(quote.currency()).isEqualTo("USD");
    }
}
