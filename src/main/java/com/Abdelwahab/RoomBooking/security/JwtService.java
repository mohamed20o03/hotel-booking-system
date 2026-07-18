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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Handles all JSON Web Token (JWT) creation, parsing, and validation for the API.
 *
 * <p>A JWT has three dot-separated parts — {@code [Header].[Payload].[Signature]}:
 * <ul>
 *   <li><strong>Header</strong> — the signing algorithm (here, HS256)</li>
 *   <li><strong>Payload</strong> — the claims (subject email, issued-at, expiry)</li>
 *   <li><strong>Signature</strong> — an HMAC-SHA256 hash of the header and payload,
 *       keyed by the server's secret</li>
 * </ul>
 * The signature makes the token tamper-proof: any modification to the payload
 * invalidates it, and the token is rejected on the next parse.
 *
 * <p><strong>Signing key.</strong> Tokens are signed with HS256 (symmetric HMAC)
 * using a Base64-encoded secret read from {@code application.yaml}; the same key
 * both signs and verifies. {@link #generateToken} issues a token subject-scoped to
 * the guest's email, {@link #extractUsername} and {@code extractAllClaims} read it
 * back, and {@link #isTokenValid} confirms the subject matches the loaded principal
 * and the token has not expired.
 */
@Service
public class JwtService {

    /**
     * The secret key used to sign and verify every JWT.
     * Must be a Base64-encoded string of at least 256 bits (32 characters) for HS256.
     * NEVER commit the real value to source control — use environment variables in production.
     */
    @Value("${spring.application.security.jwt.secret-key}")
    private String secretKey;

    /**
     * How long (in milliseconds) a token is valid after being issued.
     * Read from application.yaml. Default is 86400000 (1 day).
     */
    @Value("${spring.application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Extracts the email (username) stored in the token's "sub" (subject) claim.
     * The subject is set during token generation and identifies who the token belongs to.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Convenience method to generate a token with no extra claims.
     * Used by AuthenticationService after login or registration.
     */
    public String generateTokens(Guest guest) {
        return generateToken(new HashMap<>(), guest);
    }

    /**
     * Builds and signs the JWT.
     *
     * The token contains:
     *   - extraClaims:  Any additional data you want stored (e.g. roles, guestId)
     *   - subject:      The guest's email — used as their identifier on every request
     *   - issuedAt:     The exact timestamp when the token was created
     *   - expiration:   issuedAt + jwtExpiration — after this time the token is rejected
     *   - signature:    HMAC-SHA256 signed with the secret key — proves the token is genuine
     */
    public String generateToken(Map<String, Object> extraClaims, Guest guest) {
        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(guest.getEmail())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
            // HS256 = HMAC with SHA-256 — a symmetric algorithm where the same secret
            // key is used for both signing (server) and verification (server).
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Validates the token by checking two conditions:
     *   1. The email inside the token matches the loaded UserDetails (not tampered)
     *   2. The token's expiration date has not passed
     *
     * Both must be true for the token to be considered valid.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String userEmail = extractUsername(token);
        return (userEmail.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * Returns true if the token's expiration timestamp is before the current time.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date claim from the token payload.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor. Accepts a function that picks one specific field
     * from the Claims object (e.g. Claims::getSubject, Claims::getExpiration).
     * This avoids repeating the full parse logic for every individual claim.
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and verifies the JWT, then returns the full Claims payload.
     *
     * This is where the signature verification happens. The JJWT library:
     *   1. Decodes the token
     *   2. Recomputes the HMAC-SHA256 signature using your secret key
     *   3. Compares it against the signature in the token
     *   4. Throws SignatureException if they don't match (tampered token)
     *   5. Throws ExpiredJwtException if the token has passed its expiration date
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Decodes the Base64 secret key string from application.yaml and converts it
     * into a cryptographic Key object suitable for HMAC-SHA256 signing.
     *
     * We use Base64 encoding in the config file because raw binary key bytes
     * cannot be safely stored in a text file.
     */
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
