package com.Abdelwahab.RoomBooking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.RatePlanRate;
import com.Abdelwahab.RoomBooking.model.RateSource;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.RatePlanRateRepository;

import lombok.RequiredArgsConstructor;

/**
 * The day-by-day pricing engine.
 *
 * Given a room type, a chosen rate plan, and a stay, it walks every night in the
 * half-open interval [checkIn, checkOut) and resolves each night's price:
 *
 *   - if the rate plan has an override for that date  -> use the override (PLAN)
 *   - otherwise                                       -> use the room type's base rate (BASE)
 *
 * This is a pure function of its inputs (plus the override rows it reads), so it is
 * trivial to unit-test and produces the same total the guest was quoted.
 *
 * Worked example — base 100, "Summer Promo" override 80 for Aug 5–15,
 * stay Aug 1 (check-in) to Aug 31 (check-out) = 30 nights:
 *   Aug 1–4   -> 100 (BASE)   =  4 x 100 = 400
 *   Aug 5–15  ->  80 (PLAN)   = 11 x  80 = 880
 *   Aug 16–30 -> 100 (BASE)   = 15 x 100 = 1500
 *   Aug 31 is the checkout night and is NOT charged.
 *   total = 2780
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final RatePlanRateRepository ratePlanRateRepository;

    /**
     * A single priced night. Pure data — carries no JPA identity — so the caller
     * (ReservationService) maps it onto ReservationNight entities at persist time.
     */
    public record NightlyPrice(LocalDate date, BigDecimal amount, RateSource source) {}

    /**
     * The result of pricing a whole stay: the per-night breakdown and its total.
     */
    public record PriceQuote(String currency, BigDecimal total, List<NightlyPrice> nights) {
        public int nightCount() {
            return nights.size();
        }
    }

    /**
     * Prices a stay night-by-night. Assumes checkOut is strictly after checkIn
     * (validated upstream in ReservationRequestDTO / the booking service).
     */
    public PriceQuote quote(RoomType roomType, RatePlan ratePlan,
                            LocalDate checkIn, LocalDate checkOut) {

        // A rate plan only prices the room type it belongs to.
        if (!ratePlan.getRoomType().getId().equals(roomType.getId())) {
            throw new IllegalArgumentException(String.format(
                "Rate plan '%s' does not belong to room type '%s'.",
                ratePlan.getName(), roomType.getName()));
        }

        // Guard against silently summing two currencies into one total.
        if (!ratePlan.getCurrency().equals(roomType.getCurrency())) {
            throw new IllegalStateException(String.format(
                "Currency mismatch: rate plan is %s but room type is %s.",
                ratePlan.getCurrency(), roomType.getCurrency()));
        }

        // Load only the override rows that fall inside the stay, keyed by date.
        Map<LocalDate, BigDecimal> overrides = ratePlanRateRepository
                .findForStay(ratePlan.getId(), checkIn, checkOut)
                .stream()
                .collect(Collectors.toMap(RatePlanRate::getDate, RatePlanRate::getPrice));

        List<NightlyPrice> nights = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // Half-open walk: the checkout date is never a charged night.
        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            BigDecimal amount;
            RateSource source;
            if (overrides.containsKey(night)) {
                amount = overrides.get(night);
                source = RateSource.PLAN;
            } else {
                amount = roomType.getBasePricePerNight();
                source = RateSource.BASE;
            }
            nights.add(new NightlyPrice(night, amount, source));
            total = total.add(amount);
        }

        return new PriceQuote(roomType.getCurrency(), total, nights);
    }
}
