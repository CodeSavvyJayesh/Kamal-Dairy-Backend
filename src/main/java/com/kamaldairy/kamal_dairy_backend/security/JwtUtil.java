package com.kamaldairy.kamal_dairy_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET_KEY =
            "kamal_dairy_secret_key_which_is_long_enough_for_hs256";

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    // 🔐 Generate token
    public static String generateToken(String email,String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role",role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(KEY)
                .compact();
    }

    // 🔎 Extract email
    public static String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    // 🔎 Validate token
    public static boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 🔎 Get claims
    private static Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)   // 🔥 USE KEY HERE
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static String extractRole(String token)
    {
         return getClaims(token).get("role", String.class);
    }
}