package com.Abdelwahab.RoomBooking.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.model.Guest;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Handles all JSON Web Token (JWT) creation, parsing, and validation for the API.
 *
 * <h2>Architectural Role</h2>
 * <p>This service is the single authority for all JWT operations. It is the only
 * class that knows the signing key, the token structure, and the claim names.
 * All other components (filter, controller, blacklist service) delegate here rather
 * than parsing the token themselves, enforcing a strict single-responsibility boundary.
 *
 * <h2>Token Structure</h2>
 * <p>A JWT has three dot-separated parts — {@code [Header].[Payload].[Signature]}:
 * <ul>
 *   <li><strong>Header</strong> — algorithm declaration ({@code HS256})</li>
 *   <li><strong>Payload</strong> — claims: {@code sub} (email), {@code jti} (a
 *       <em>compound ID</em> of the form {@code <userId>:<NanoID>}),
 *       {@code iat} (issued-at), {@code exp} (expiry)</li>
 *   <li><strong>Signature</strong> — HMAC-SHA256 over header + payload using the
 *       server secret; any payload mutation invalidates it</li>
 * </ul>
 *
 * <h2>Why a Compound {@code jti} ({@code <userId>:<NanoID>})?</h2>
 * <p>The {@code jti} (JWT ID) claim is the stable handle used by the token blacklist
 * to revoke individual tokens. Rather than a bare NanoID, we embed a <strong>compound
 * identifier</strong> combining the owning user's ID and a per-token NanoID, joined by
 * a single colon — e.g. {@code "12345:V1StGXR8_Z5jdHi6B-myT"}. This unlocks the
 * <strong>two-tier revocation</strong> model without adding a second claim or a second
 * parse:
 * <ol>
 *   <li><strong>Token-level revocation.</strong> The full compound value is the unique
 *       blacklist key ({@code blacklist:token:<userId>:<NanoID>}), so a single token can
 *       be revoked (e.g. one-device logout) exactly as before.</li>
 *   <li><strong>User-level revocation.</strong> The {@code <userId>} prefix can be
 *       isolated in O(1) from any token (see {@link #extractUserIdFromToken}) and checked
 *       against a global per-user ban key ({@code blacklist:user:<userId>}). This lets the
 *       backend invalidate <em>every</em> token a user holds — across all devices — with a
 *       single Redis write, for scenarios like an admin ban, a password change, or an
 *       explicit "log out everywhere" action. No enumeration of individual {@code jti}s
 *       is required.</li>
 * </ol>
 * <p>Because the user ID travels inside the signed payload, the prefix cannot be forged:
 * tampering with it invalidates the HMAC signature and the token is rejected at parse time.
 *
 * <h2>Why NanoID for the token component?</h2>
 * <p>The per-token component of the {@code jti} is a 21-character NanoID from the
 * {@code com.aventrix.jnanoid} library instead of a UUID for three concrete reasons:
 * <ol>
 *   <li><strong>Payload efficiency.</strong> A NanoID at 21 characters is materially
 *       shorter than a UUID at 36 characters (including hyphens). Because this value
 *       is embedded in every JWT, which in turn is sent with every request inside the
 *       HttpOnly cookie, minimising its size reduces per-request network overhead
 *       across the entire system lifetime.</li>
 *   <li><strong>Equivalent collision resistance.</strong> The default NanoID alphabet
 *       has 64 characters; 64^21 ≈ 2^126 possible values — comparable to UUID v4's
 *       2^122. The probability of a collision in a system with millions of active
 *       tokens is astronomically small.</li>
 *   <li><strong>URL-safe by design.</strong> All characters in the NanoID alphabet
 *       are URL-safe and Base64url-safe, so no additional encoding is needed when
 *       the {@code jti} is embedded in the JWT payload.</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is a stateless Spring singleton. All fields are {@code @Value}-injected
 * at startup and never mutated afterward. {@link #extractAllClaims} and
 * {@link #generateToken} are thread-safe: JJWT's builder and parser are instantiated
 * per-call with no shared mutable state.
 *
 * <h2>Security Contract</h2>
 * <ul>
 *   <li>The signing key is loaded from an environment variable via {@code application.yaml};
 *       it is never committed to source control.</li>
 *   <li>Token parsing in {@link #extractAllClaims} rejects any token with an invalid
 *       signature ({@code SignatureException}) or past its expiry
 *       ({@code ExpiredJwtException}) before any claim is returned to the caller.</li>
 *   <li>Roles and authorities are intentionally absent from the payload — they are
 *       loaded fresh from the database on every request to prevent privilege escalation
 *       via a stolen-but-unexpired token.</li>
 * </ul>
 */
@Service
public class JwtService {

    /**
     * The secret key used to sign and verify every JWT.
     * Must be a Base64-encoded string of at least 256 bits (32 bytes) for HS256.
     * Loaded from the {@code JWT_SECRET} environment variable via {@code application.yaml}.
     */
    @Value("${spring.application.security.jwt.secret-key}")
    private String secretKey;

    /**
     * How long (in milliseconds) a token is valid after being issued.
     * Loaded from the {@code JWT_EXPIRATION} environment variable. Default: 86400000 (1 day).
     */
    @Value("${spring.application.security.jwt.expiration}")
    private long jwtExpiration;

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the email (subject) stored in the token's {@code sub} claim.
     * This is the primary identity carrier used to re-load the guest from the database.
     *
     * @param token a signed JWT string
     * @return the guest's email address
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the full compound JWT ID ({@code jti}) claim — the
     * {@code <userId>:<NanoID>} value embedded at token generation time. This is the
     * stable, unique handle for <strong>token-level</strong> revocation lookups in the
     * blacklist store (Redis).
     *
     * <p>The value is compound (e.g. {@code "12345:V1StGXR8_Z5jdHi6B-myT"}): the
     * {@code <userId>} prefix supports user-level revocation via
     * {@link #extractUserIdFromToken}, while the whole string uniquely identifies this
     * one token. See the class-level Javadoc for the full rationale.
     *
     * @param token a signed JWT string
     * @return the compound {@code <userId>:<NanoID>} identifier of this token
     */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Isolates the owning user's ID from the compound {@code jti} claim in O(1),
     * without a database lookup. Given a {@code jti} of {@code "12345:V1StGXR8_..."},
     * this returns {@code "12345"}.
     *
     * <p>This is the enabler for <strong>user-level (global) revocation</strong>: the
     * {@link JwtAuthenticationFilter} extracts the user ID from every incoming token and
     * checks it against a single per-user ban key in Redis, invalidating all of that
     * user's tokens at once (admin ban, password change, "log out everywhere") without
     * enumerating individual {@code jti}s.
     *
     * <p>Because the user ID lives inside the signed payload, it cannot be forged: any
     * tampering with the prefix breaks the HMAC signature and the token is rejected at
     * parse time in {@link #extractAllClaims}.
     *
     * @param token a signed JWT string
     * @return the {@code <userId>} prefix of the compound {@code jti}, or the full
     *         {@code jti} value if it contains no {@code ':'} separator (defensive
     *         fallback for tokens minted before the compound format)
     */
    public String extractUserIdFromToken(String token) {
        String jti = extractJti(token);
        if (jti == null) {
            return null;
        }
        int separatorIndex = jti.indexOf(':');
        return separatorIndex >= 0 ? jti.substring(0, separatorIndex) : jti;
    }

    /**
     * Returns the number of seconds remaining until this token expires.
     *
     * <p>This value is passed directly to the Redis blacklist entry's TTL during
     * logout so the blacklist entry self-expires at the exact moment the token would
     * have expired anyway. This prevents unbounded growth of the Redis keyspace while
     * ensuring the entry covers the full window during which the token could be replayed.
     *
     * @param token a signed JWT string whose expiration has not yet passed
     * @return remaining lifetime in whole seconds; returns {@code 0} if already expired
     */
    public long getRemainingExpirationInSeconds(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0L, remainingMs / 1000);
    }

    /**
     * Convenience overload: generates a token for a guest with no additional claims.
     * This is the primary entry point called by {@code AuthenticationService}.
     *
     * @param guest the authenticated guest; their email becomes the {@code sub} claim
     * @return a compact, signed JWT string
     */
    public String generateTokens(Guest guest) {
        return generateToken(new HashMap<>(), guest);
    }

    /**
     * Builds and signs a JWT for the given guest.
     *
     * <p>The token payload contains:
     * <ul>
     *   <li>{@code sub} — the guest's email; the primary identity claim</li>
     *   <li>{@code jti} — a compound {@code <userId>:<NanoID>} identifier; unique per
     *       token and used for both token-level and user-level revocation</li>
     *   <li>{@code iat} — the issue timestamp in epoch seconds</li>
     *   <li>{@code exp} — issue time + configured expiration; the token is rejected
     *       automatically after this point by the JJWT parser</li>
     * </ul>
     *
     * <p>Roles are intentionally excluded. They are re-loaded from the database on
     * every authenticated request in {@code JwtAuthenticationFilter}, so a token
     * captured before a privilege demotion cannot be used to escalate access.
     *
     * @param extraClaims additional key-value pairs to embed in the payload (may be empty)
     * @param guest       the authenticated guest whose email is the token subject
     * @return a compact, signed JWT string
     */
    public String generateToken(Map<String, Object> extraClaims, Guest guest) {
        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(guest.getEmail())
            // Compound jti: "<userId>:<NanoID>". The userId prefix enables O(1)
            // user-level revocation (see extractUserIdFromToken); the NanoID makes
            // each token individually unique and revocable.
            .setId(guest.getId() + ":" + NanoIdUtils.randomNanoId())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Validates the token by checking two conditions:
     * <ol>
     *   <li>The {@code sub} claim matches the loaded {@link UserDetails} (prevents
     *       subject-swap attacks on a structurally valid token)</li>
     *   <li>The token has not expired ({@code exp} is in the future)</li>
     * </ol>
     *
     * <p><strong>Note:</strong> blacklist checks are NOT performed here. They are a
     * separate, earlier gate inside {@code JwtAuthenticationFilter} so that a revoked
     * token never even reaches this validation step.
     *
     * @param token       a signed JWT string
     * @param userDetails the principal loaded from the database for the token's subject
     * @return {@code true} if both conditions are satisfied; {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String userEmail = extractUsername(token);
        return userEmail.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Generic claim extractor. Accepts a resolver function that selects one field
     * from the verified {@link Claims} object, avoiding duplication of the full
     * parse-and-verify flow for each individual claim.
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Parses and cryptographically verifies the JWT, returning its full claim set.
     *
     * <p>This is where signature verification occurs. The JJWT library:
     * <ol>
     *   <li>Decodes the three dot-separated segments</li>
     *   <li>Recomputes the HMAC-SHA256 signature from the header + payload</li>
     *   <li>Compares it against the signature segment — throws {@code SignatureException}
     *       on mismatch (tampered token)</li>
     *   <li>Checks {@code exp} — throws {@code ExpiredJwtException} if past</li>
     * </ol>
     *
     * <p>Claims are never returned until the signature is proven. A tampered payload
     * cannot be read — the exception is thrown first.
     *
     * @throws io.jsonwebtoken.JwtException for any parse/validation failure
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Decodes the Base64-encoded secret from configuration into a cryptographic
     * {@link Key} for HMAC-SHA256. Base64 encoding is necessary because raw binary
     * key bytes cannot be safely stored in a text configuration file.
     */
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
