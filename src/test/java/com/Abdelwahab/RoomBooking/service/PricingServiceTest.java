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

/**
 * Plain Mockito unit test for PricingService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); the RatePlanRateRepository is a @Mock injected
 * into the service). It pins the day-by-day rate engine's arithmetic in isolation: the
 * half-open stay interval (the checkout night is never charged), the fall-through from
 * a rate-plan override to the room type's base rate on a per-night basis with correct
 * RateSource tagging, that the quote currency is taken from the room type, and the
 * fail-fast guard rejecting a rate plan that belongs to a different room type before
 * the database is queried.
 */
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

    /**
     * Given a three-night stay with no rate-plan overrides;
     * when quoted; then all three nights price at the base rate (3 x 100 = 300.00) in the
     * room type's currency and every night is tagged RateSource.BASE.
     */
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

    /**
     * Given a one-night stay (check-in Jun 1, checkout Jun 2) with no overrides;
     * when quoted; then exactly one night (Jun 1) is charged at the base rate for a total
     * of 100.00 — the checkout day is excluded by the half-open interval.
     */
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

    /**
     * Given a six-night stay with plan overrides of 80 on three of the nights (the
     * worked example); when quoted; then the nights outside the promo fall through to the
     * base rate, the total is 540.00, and each night is tagged BASE or PLAN accordingly.
     */
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

    /**
     * Given a two-night stay where findForStay returns a single override for the first
     * night; when quoted; then the first night uses the override (PLAN) and the second
     * falls through to the base rate (BASE) for a total of 170.00 — documenting that the
     * quote trusts the repository to have windowed the overrides to the stay.
     */
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

    /**
     * Given a rate plan whose room type differs from the one being priced;
     * when a quote is attempted; then an IllegalArgumentException is raised and the
     * repository is never queried — the mismatch is caught fast, before any database hit.
     */
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

    /**
     * Given the room type's currency is set to USD;
     * when a stay is quoted; then the quote reports USD — the currency is sourced from
     * the room type, not the rate plan.
     */
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
