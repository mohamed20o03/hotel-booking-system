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
 * <p><strong>Responsibility.</strong> Given a room type, a chosen rate plan, and a
 * stay, it walks every night in the half-open interval {@code [checkIn, checkOut)}
 * and resolves each night's price:
 * <ul>
 *   <li>if the rate plan has an override for that date, use the override
 *       ({@code PLAN});</li>
 *   <li>otherwise fall back to the room type's base rate ({@code BASE}).</li>
 * </ul>
 *
 * <p>This is a pure function of its inputs (plus the override rows it reads), so it
 * is trivial to unit-test and produces the same total the guest was quoted.
 * {@link ReservationService} freezes the returned per-night breakdown onto the
 * reservation at booking time, so a later rate change never rewrites a live booking.
 *
 * <p><strong>Worked example</strong> — base 100, a "Summer Promo" override of 80 for
 * Aug 5–15, stay Aug 1 (check-in) to Aug 31 (check-out) = 30 nights:
 * <ul>
 *   <li>Aug 1–4   &rarr; 100 (BASE)  = 4 &times; 100 = 400</li>
 *   <li>Aug 5–15  &rarr;  80 (PLAN)  = 11 &times; 80 = 880</li>
 *   <li>Aug 16–30 &rarr; 100 (BASE)  = 15 &times; 100 = 1500</li>
 *   <li>Aug 31 is the checkout night and is NOT charged.</li>
 *   <li>total = 2780</li>
 * </ul>
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repository; safe for concurrent request threads.
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
     * Prices a stay night-by-night, returning the per-night breakdown and total.
     *
     * <p>Loads only the rate-plan override rows that fall inside the stay and walks
     * the half-open interval {@code [checkIn, checkOut)}, so the checkout date is
     * never a charged night. Assumes {@code checkOut} is strictly after
     * {@code checkIn} (validated upstream in {@code ReservationRequestDTO} and the
     * booking service). Not transactional in its own right; when invoked from a
     * booking it participates in the caller's transaction.
     *
     * @param roomType the room type being priced; supplies the base rate and currency.
     * @param ratePlan the chosen rate plan; must belong to {@code roomType}.
     * @param checkIn  the first night of the stay (inclusive).
     * @param checkOut the checkout date (exclusive); must be strictly after
     *                 {@code checkIn}.
     * @return a {@link PriceQuote} carrying the currency, total, and ordered per-night
     *         breakdown.
     * @throws IllegalArgumentException if the rate plan does not belong to the given
     *         room type.
     */
    public PriceQuote quote(RoomType roomType, RatePlan ratePlan,
                            LocalDate checkIn, LocalDate checkOut) {

        // A rate plan only prices the room type it belongs to.
        if (!ratePlan.getRoomType().getId().equals(roomType.getId())) {
            throw new IllegalArgumentException(String.format(
                "Rate plan '%s' does not belong to room type '%s'.",
                ratePlan.getName(), roomType.getName()));
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
