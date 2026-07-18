package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled background task that releases reservation holds that were never paid,
 * returning their held inventory to the sellable allotment.
 *
 * <p><strong>Role in the reservation state machine.</strong> This is the driver of
 * the {@code PENDING → EXPIRED} transition. A booking holds inventory only for a
 * bounded window ({@code holdExpiresAt}); once that window lapses without full
 * payment, this sweeper reclaims the nights so abandoned carts do not sit on scarce
 * rooms.
 *
 * <p><strong>Per-hold isolation.</strong> The sweep is split in two so each expiry
 * is isolated: it first reads the IDs of lapsed {@code PENDING} holds, then expires
 * each one via {@link ReservationService#expireHold(Long)} — a separate call so
 * Spring's transactional proxy gives each its own transaction. A failure or lock
 * conflict on one hold therefore does not roll back the others.
 *
 * <p><strong>Concurrency — racing a payment.</strong> A payment landing on the same
 * reservation as the sweep is resolved by the reservation's {@code @Version}
 * optimistic lock. If {@link PaymentService} commits its confirmation first, either
 * {@code expireHold} re-reads the row as no longer {@code PENDING} and skips it, or
 * the version check fails and raises {@link OptimisticLockingFailureException},
 * which this sweeper catches and swallows for that one hold. Either way the payment
 * wins and the confirmed booking is never expired.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton; {@code sweep}
 * holds no cross-invocation state. The {@code fixedDelay}-style cron scheduling
 * ensures a slow sweep never overlaps itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpirySweeper {

    private final ReservationService reservationService;

    /**
     * Finds and expires every lapsed unpaid hold, releasing each one's inventory.
     *
     * <p>Reads the ids of {@code PENDING} holds whose window has passed as of now,
     * then expires each in its own transaction. Holds lost to a concurrent payment
     * raise {@link OptimisticLockingFailureException} and are skipped rather than
     * clobbering the payment. Invoked by the scheduler on the configured cron; not
     * meant to be called directly.
     *
     * <p>Runs shortly after each minute (avoiding the {@code :00} mark).
     */
    @Scheduled(cron = "17 * * * * *")
    public void sweep() {
        List<Long> lapsedIds = reservationService.findLapsedHoldIds(LocalDateTime.now());
        if (lapsedIds.isEmpty()) {
            return;
        }

        int expired = 0;
        for (Long id : lapsedIds) {
            try {
                reservationService.expireHold(id);
                expired++;
            } catch (OptimisticLockingFailureException ex) {
                // A payment committed on this hold as we tried to expire it —
                // the payment wins; leave this reservation alone.
                log.debug("Skipped expiring reservation {}: concurrent update won.", id);
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid reservation hold(s).", expired);
        }
    }
}
