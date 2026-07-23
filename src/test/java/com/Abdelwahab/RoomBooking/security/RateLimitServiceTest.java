package com.Abdelwahab.RoomBooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RateLimitService}'s counting and fail-open policy, with a
 * mocked {@link StringRedisTemplate} (no container needed).
 */
public class RateLimitServiceTest {

    private RateLimitService serviceWith(StringRedisTemplate redis) {
        // max 3 attempts, 60s window
        return new RateLimitService(redis, 3, 60);
    }

    /**
     * Given Redis returns increasing counts; when tryAcquire is called; then it
     * returns true up to the max and false once the count exceeds it.
     */
    @Test
    public void allowsUpToMaxThenRejects() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        // Simulate the counter climbing 1..4 across four calls.
        when(ops.increment(anyString())).thenReturn(1L, 2L, 3L, 4L);

        RateLimitService service = serviceWith(redis);

        assertThat(service.tryAcquire("ip")).isTrue();   // 1
        assertThat(service.tryAcquire("ip")).isTrue();   // 2
        assertThat(service.tryAcquire("ip")).isTrue();   // 3
        assertThat(service.tryAcquire("ip")).isFalse();  // 4 > max
    }

    /**
     * Given Redis is down (increment throws a data-access exception); when
     * tryAcquire is called; then it FAILS OPEN and returns true — a cache outage
     * must not lock users out of login.
     */
    @Test
    public void failsOpenWhenRedisUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitService service = serviceWith(redis);

        assertThat(service.tryAcquire("ip")).isTrue();
    }
}
