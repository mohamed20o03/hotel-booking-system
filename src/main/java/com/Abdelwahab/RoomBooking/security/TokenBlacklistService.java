package com.Abdelwahab.RoomBooking.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages server-side JWT revocation using Redis as the blacklist store.
 *
 * <h2>Architectural Role</h2>
 * <p>This service is the single port to the revocation subsystem. It is injected into
 * both {@link JwtAuthenticationFilter} (to check on every authenticated request) and
 * {@code AuthController} (to record a revocation on logout). No other component
 * interacts with Redis for revocation purposes, keeping the concern isolated.
 *
 * <h2>Why Redis?</h2>
 * <p>A stateless JWT architecture requires an external store for revocation because
 * the token itself carries no server-side state. Redis is chosen over a relational
 * database table for three reasons:
 * <ol>
 *   <li><strong>Sub-millisecond reads.</strong> Every authenticated request checks this
 *       store before granting access. A relational DB query would add latency and
 *       connection-pool pressure on the hot path; Redis keeps the overhead negligible.</li>
 *   <li><strong>Native TTL support.</strong> Redis key expiry ({@code SETEX} / {@code Duration})
 *       is atomic. We set the blacklist entry's TTL to match the token's remaining
 *       lifetime exactly — when the token would have expired anyway, the Redis key
 *       self-deletes. This prevents unbounded keyspace growth without any background
 *       cleanup job.</li>
 *   <li><strong>Operational simplicity.</strong> Revoked-token counts are bounded by
 *       active-token counts (one entry per live token, auto-expiring). No purge job,
 *       no archive table, no index maintenance.</li>
 * </ol>
 *
 * <h2>Why store the revocation reason?</h2>
 * <p>Storing the reason string (e.g. {@code "LOGOUT"}, {@code "ADMIN_BAN"},
 * {@code "PASSWORD_CHANGE"}, {@code "SECURITY_BREACH"}) rather than a plain boolean
 * {@code "true"} costs nothing at the Redis level (both are strings) but unlocks
 * actionable security monitoring:
 * <ul>
 *   <li>The filter can log the exact reason when a revoked token is replayed, enabling
 *       targeted alerting on {@code "ADMIN_BAN"} or {@code "SECURITY_BREACH"} attempts.</li>
 *   <li>Audit systems and SIEM tools can consume the reason to triage incidents without
 *       querying the application layer.</li>
 *   <li>Future product features (e.g. "you were logged out because your password changed")
 *       can be driven by the stored reason without a schema migration.</li>
 * </ul>
 *
 * <h2>Two-Tier Revocation</h2>
 * <p>This service supports two independent, complementary revocation tiers:
 * <ol>
 *   <li><strong>Token-level</strong> ({@code blacklist:token:<jti>}) — revokes one
 *       specific token by its compound {@code jti}. Used for single-device logout.</li>
 *   <li><strong>User-level (global)</strong> ({@code blacklist:user:<userId>}) — revokes
 *       <em>every</em> token a user holds with a single write, by banning the user ID that
 *       every one of their tokens carries in its compound {@code jti} prefix. Used for
 *       admin bans, password changes, and "log out from all devices".</li>
 * </ol>
 * <p>The filter checks the user-level (broad) tier first, then the token-level (narrow)
 * tier — see {@link JwtAuthenticationFilter}. Either hit denies the request.
 *
 * <h2>Key Space</h2>
 * <p>Token-level entries live under {@code blacklist:token:<jti>} (where {@code <jti>} is
 * the compound {@code <userId>:<NanoID>}); user-level entries under
 * {@code blacklist:user:<userId>}. The distinct prefixes keep the two tiers from colliding
 * and isolate all revocation keys from any other Redis data the application may write.
 *
 * <h2>Thread Safety</h2>
 * <p>Stateless Spring singleton. {@link StringRedisTemplate} is thread-safe; all
 * methods here are effectively atomic from the application's perspective — each
 * operation is a single Redis command with no intermediate shared state.
 *
 * <h2>Error Handling</h2>
 * <p>Redis connectivity errors propagate as unchecked {@code RedisException}
 * sub-types. The calling filter treats any exception from this service as a hard
 * failure and denies access (fail-closed). This is the safe default for a security
 * gate: a degraded Redis connection must not grant access to revoked tokens.
 *
 * @see JwtAuthenticationFilter
 * @see JwtService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    /** Namespace for token-level (single-token) revocation keys: {@code blacklist:token:<jti>}. */
    private static final String TOKEN_KEY_PREFIX = "blacklist:token:";

    /** Namespace for user-level (global) revocation keys: {@code blacklist:user:<userId>}. */
    private static final String USER_KEY_PREFIX = "blacklist:user:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Records a token as revoked in Redis with a reason and a self-expiring TTL.
     *
     * <p>The key is set with an explicit {@link Duration} TTL equal to the token's
     * remaining validity window. When that duration elapses, Redis atomically removes
     * the key — meaning the blacklist never retains entries for tokens that have
     * already expired on their own. This ensures O(active-tokens) space complexity
     * with zero cleanup overhead.
     *
     * <p>The stored value is the {@code reason} string rather than a boolean so that
     * downstream consumers (e.g. the filter's security warning log, audit pipelines)
     * have actionable context without an additional lookup.
     *
     * @param jti                 the unique NanoID identifier embedded in the JWT's
     *                            {@code jti} claim; used as the Redis key discriminator
     * @param expirationInSeconds the remaining lifetime of the token in seconds;
     *                            becomes the Redis key TTL — must be positive
     * @param reason              a short, machine-readable description of why this
     *                            token was revoked (e.g. {@code "LOGOUT"},
     *                            {@code "ADMIN_BAN"}, {@code "PASSWORD_CHANGE"},
     *                            {@code "SECURITY_BREACH"})
     */
    public void blacklist(String jti, long expirationInSeconds, String reason) {
        stringRedisTemplate.opsForValue().set(
            TOKEN_KEY_PREFIX + jti,
            reason,
            Duration.ofSeconds(expirationInSeconds)
        );
        log.info("Token revoked [jti={}] reason={} ttl={}s", jti, reason, expirationInSeconds);
    }

    /**
     * Returns {@code true} if the given {@code jti} is present in the Redis blacklist.
     *
     * <p>Called on the hot path of every authenticated request inside
     * {@link JwtAuthenticationFilter}. The underlying command is a Redis {@code EXISTS},
     * which completes in O(1) time regardless of blacklist size.
     *
     * @param jti the unique NanoID token identifier to check
     * @return {@code true} if the token has been revoked and its blacklist entry has
     *         not yet expired; {@code false} otherwise
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_KEY_PREFIX + jti));
    }

    /**
     * Returns the revocation reason stored for the given {@code jti}, or
     * {@code null} if the token is not blacklisted (or its entry has expired).
     *
     * <p>This method is used by the filter to include the specific reason in security
     * warning logs when a revoked token replay is detected, enabling operators to
     * distinguish a benign duplicate logout from a hostile {@code ADMIN_BAN} replay
     * attempt.
     *
     * @param jti the unique NanoID token identifier to query
     * @return the stored reason string (e.g. {@code "LOGOUT"}, {@code "ADMIN_BAN"}),
     *         or {@code null} if no blacklist entry exists for this {@code jti}
     */
    public String getRevocationReason(String jti) {
        return stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + jti);
    }

    // ─────────────────────────────────────────────────────────────
    // Tier 2: Global user-level revocation
    // ─────────────────────────────────────────────────────────────

    /**
     * Revokes <strong>every</strong> token a user currently holds with a single Redis
     * write, by recording a global ban key {@code blacklist:user:<userId>}.
     *
     * <p>Whereas {@link #blacklist} targets one token by its compound {@code jti}, this
     * method invalidates a user's entire token population at once — regardless of how
     * many devices they are logged in from — without enumerating individual {@code jti}s.
     * The filter isolates the {@code <userId>} prefix from each incoming token's compound
     * {@code jti} and consults this key before the per-token check, so a single write
     * here immediately closes every active session.
     *
     * <p><strong>Choosing the TTL.</strong> The ban only needs to outlive the tokens it
     * targets. Since no token can live longer than the configured JWT expiration, a TTL
     * equal to that maximum lifespan (e.g. {@code 86400} seconds) guarantees the ban
     * covers every token issued <em>before</em> it, after which the key self-expires and
     * the user may authenticate freshly. Tokens minted <em>after</em> the ban are
     * unaffected — the intended behaviour for a password change or a re-enabled account.
     *
     * @param userId              the owning user's ID, matching the {@code <userId>}
     *                            prefix embedded in each token's compound {@code jti}
     * @param expirationInSeconds the ban's TTL in seconds; should be at least the maximum
     *                            JWT lifespan so it outlives every pre-existing token
     * @param reason              a short, machine-readable cause (e.g. {@code "ADMIN_BAN"},
     *                            {@code "PASSWORD_CHANGED"}, {@code "LOGOUT_ALL_DEVICES"})
     */
    public void banUserGlobally(String userId, long expirationInSeconds, String reason) {
        stringRedisTemplate.opsForValue().set(
            USER_KEY_PREFIX + userId,
            reason,
            Duration.ofSeconds(expirationInSeconds)
        );
        log.info("User globally banned [userId={}] reason={} ttl={}s", userId, reason, expirationInSeconds);
    }

    /**
     * Returns {@code true} if a global ban key exists for the given user.
     *
     * <p>Checked on the hot path of every authenticated request inside
     * {@link JwtAuthenticationFilter}, <em>before</em> the per-token blacklist lookup:
     * a globally banned user is rejected even if the specific token they present was
     * never individually revoked. The underlying command is a Redis {@code EXISTS},
     * O(1) regardless of how many users are banned.
     *
     * @param userId the user ID isolated from the token's compound {@code jti}
     * @return {@code true} if the user is globally banned and the ban has not expired;
     *         {@code false} otherwise
     */
    public boolean isUserBanned(String userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(USER_KEY_PREFIX + userId));
    }

    /**
     * Returns the reason stored for a user's global ban, or {@code null} if the user is
     * not banned (or the ban has expired).
     *
     * <p>Used by the filter to emit an actionable security-warning log when a banned
     * user's token is replayed, letting operators distinguish a routine
     * {@code LOGOUT_ALL_DEVICES} from a hostile {@code ADMIN_BAN} evasion attempt.
     *
     * @param userId the user ID isolated from the token's compound {@code jti}
     * @return the stored reason string (e.g. {@code "ADMIN_BAN"},
     *         {@code "PASSWORD_CHANGED"}), or {@code null} if no ban exists
     */
    public String getUserBanReason(String userId) {
        return stringRedisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
    }
}
