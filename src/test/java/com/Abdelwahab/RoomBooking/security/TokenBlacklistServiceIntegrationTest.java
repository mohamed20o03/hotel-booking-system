package com.Abdelwahab.RoomBooking.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.Abdelwahab.RoomBooking.AbstractIntegrationTest;

/**
 * Integration test for {@link TokenBlacklistService}.
 * 
 * This test actually connects to the real Redis container defined in
 * {@link AbstractIntegrationTest} to ensure the service correctly stores
 * and retrieves tokens and user bans.
 */
public class TokenBlacklistServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    public void cleanup() {
        // Clean up Redis after each test so state does not leak
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    public void blacklist_storesTokenWithCorrectTtl() throws InterruptedException {
        String jti = "test-jti-123";
        String reason = "LOGOUT";
        long ttlSeconds = 2; // Short TTL for testing expiration
        
        tokenBlacklistService.blacklist(jti, ttlSeconds, reason);

        // 1. Verify the token is immediately blacklisted
        assertThat(tokenBlacklistService.isBlacklisted(jti)).isTrue();
        assertThat(tokenBlacklistService.getRevocationReason(jti)).isEqualTo(reason);

        // 2. Wait for expiration
        Thread.sleep(2500); // Wait 2.5s

        // 3. Verify the token is removed automatically by Redis
        assertThat(tokenBlacklistService.isBlacklisted(jti)).isFalse();
        assertThat(tokenBlacklistService.getRevocationReason(jti)).isNull();
    }

    @Test
    public void banUserGlobally_storesUserBanWithCorrectTtl() throws InterruptedException {
        String userId = "42";
        String reason = "ADMIN_BAN";
        long ttlSeconds = 2; // Short TTL for testing expiration
        
        tokenBlacklistService.banUserGlobally(userId, ttlSeconds, reason);

        // 1. Verify the user is immediately banned
        assertThat(tokenBlacklistService.isUserBanned(userId)).isTrue();
        assertThat(tokenBlacklistService.getUserBanReason(userId)).isEqualTo(reason);

        // 2. Wait for expiration
        Thread.sleep(2500); // Wait 2.5s

        // 3. Verify the ban is removed automatically by Redis
        assertThat(tokenBlacklistService.isUserBanned(userId)).isFalse();
        assertThat(tokenBlacklistService.getUserBanReason(userId)).isNull();
    }

    @Test
    public void tokenIsNotBlacklisted_whenNeverAdded() {
        assertThat(tokenBlacklistService.isBlacklisted("non-existent-jti")).isFalse();
        assertThat(tokenBlacklistService.getRevocationReason("non-existent-jti")).isNull();
    }
}
