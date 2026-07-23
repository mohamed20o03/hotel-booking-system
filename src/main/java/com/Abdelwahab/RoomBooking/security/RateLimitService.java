package com.Abdelwahab.RoomBooking.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-IP request throttling for the public auth endpoints, backed by a Redis
 * fixed-window counter.
 *
 * <p><strong>Algorithm.</strong> One counter key per IP, {@code INCR}-ed on each
 * attempt. The first increment in a window sets a TTL equal to the window length,
 * so the counter self-resets when the window elapses — no cleanup job. When the
 * count exceeds {@code maxAttempts}, the caller is over the limit for the rest of
 * the window.
 *
 * <p><strong>Fail-open, by design.</strong> Unlike {@link TokenBlacklistService}
 * (a security gate that fails <em>closed</em> — deny on doubt), this is a
 * <em>protection</em>, not a gate. A Redis outage must not lock every user out of
 * login, so a Redis error is logged at WARN and the request is allowed through.
 *
 * @see RateLimitFilter
 */
@Slf4j
@Service
public class RateLimitService {

    /** Namespace for auth rate-limit counters: {@code ratelimit:auth:<ip>}. */
    private static final String KEY_PREFIX = "ratelimit:auth:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;

    public RateLimitService(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.auth.max-attempts}") int maxAttempts,
            @Value("${app.rate-limit.auth.window-seconds}") long windowSeconds) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Records one attempt from the given client IP and reports whether it is
     * within the allowed window budget.
     *
     * @param ip the client IP the counter is keyed on
     * @return {@code true} if the attempt is allowed; {@code false} once the IP has
     *         exceeded {@code maxAttempts} within the window
     */
    public boolean tryAcquire(String ip) {
        String key = KEY_PREFIX + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            // increment() only returns null on a connection fault; treat as allowed.
            if (count == null) {
                return true;
            }
            // First hit in this window — stamp the TTL so the counter self-resets.
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= maxAttempts;
        } catch (DataAccessException ex) {
            // Fail open: a degraded cache must not block legitimate logins.
            log.warn("Rate-limit check skipped — Redis unavailable: {}", ex.getMessage());
            return true;
        }
    }

    /** Window length in seconds, surfaced for the {@code Retry-After} header. */
    public long getWindowSeconds() {
        return window.toSeconds();
    }
}
