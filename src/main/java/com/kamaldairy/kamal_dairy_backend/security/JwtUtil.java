package com.kamaldairy.kamal_dairy_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT helper.
 *
 * The signing key is NO LONGER hardcoded. It is read from the "jwt.secret"
 * property, which in every environment is backed by the JWT_SECRET environment
 * variable. The application refuses to start if it is missing or too short,
 * so a misconfigured deploy fails loudly instead of silently signing tokens
 * with a key that is public on GitHub.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Export the JWT_SECRET environment variable " +
                    "with at least 32 characters of random data before starting the app.");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret is too short (" + keyBytes.length + " bytes). " +
                    "HS256 requires at least 32 bytes. Generate one with: openssl rand -base64 48");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // Generate token
    public String generateToken(String email, String role) {
        Date now = new Date();

        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    // Extract email (subject)
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    // Extract role claim
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // Validate signature + expiry
    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
