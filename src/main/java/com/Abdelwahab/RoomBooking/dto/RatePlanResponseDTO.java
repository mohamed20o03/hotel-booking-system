package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO representing one bookable rate plan for a searched stay, nested
 * within {@link AvailabilityResponseDTO} in the availability response. It is an
 * outbound wire shape; the {@code RatePlan} JPA entity is never serialized
 * directly. The price is computed day-by-day for the requested dates (plan
 * overrides plus base-rate fallback), so it reflects the real total the guest
 * would pay rather than a single flat nightly figure.
 *
 * @param ratePlanId the plan's identifier, the value quoted back in a
 *        {@code ReservationRequestDTO} to book this option.
 * @param name the plan's display name.
 * @param currency the ISO 4217 code the total is quoted in.
 * @param minStayNights the minimum number of nights the plan requires.
 * @param breakfastIncluded whether breakfast is bundled.
 * @param isRefundable whether the plan permits a refund on cancellation.
 * @param nights the number of nights in the searched stay.
 * @param totalPrice the fully computed price for the whole stay under this plan.
 */
public record RatePlanResponseDTO(
    Long ratePlanId,
    String name,
    String currency,
    int minStayNights,
    boolean breakfastIncluded,
    boolean isRefundable,
    int nights,
    BigDecimal totalPrice
) {}
