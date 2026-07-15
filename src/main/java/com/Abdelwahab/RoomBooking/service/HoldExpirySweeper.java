package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically releases reservation holds that were never paid.
 *
 * The sweep is split in two so each expiry is isolated: it first reads the IDs of
 * lapsed PENDING holds, then expires each one via {@link ReservationService#expireHold(Long)}
 * — a separate call so Spring's proxy gives each its own transaction. A failure
 * or lock conflict on one hold therefore doesn't roll back the others.
 *
 * A payment racing the sweep is handled by the reservation's @Version: if a
 * payment commits first, expireHold sees the row is no longer PENDING (or loses
 * the version check) and that hold is skipped — the payment always wins.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpirySweeper {

    private final ReservationService reservationService;

    // Runs shortly after each minute (avoiding the :00 mark). fixedDelay so a slow
    // sweep never overlaps itself.
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
