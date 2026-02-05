package com.kamaldairy.kamal_dairy_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import javax.xml.crypto.Data;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET_KEY = "kamal_dairy_secret_key_which_is_long_enough_for_hs256";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    public static String generateToken(String email)
    {
        return Jwts.builder().setSubject(email).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 86400000)).signWith(KEY).compact();
    }
    // extract email from the token
    public static String extractEmail(String token)
    {
        Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).
                parseClaimsJws(token).getBody();
        return claims.getSubject();
    }
    // validate token expiry
    public static boolean isTokenValid(String token)
    {
         try{
             Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token);
             return true;
         }catch( Exception e)
         {
              return false;
         }
    }
}
