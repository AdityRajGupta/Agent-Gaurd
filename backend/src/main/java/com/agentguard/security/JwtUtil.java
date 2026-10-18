package com.agentguard.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  private final SecretKey key;
  private final long expiryMillis;

  public JwtUtil(
      @Value("${agentguard.jwt.secret}") String secret,
      @Value("${agentguard.jwt.expiry-hours}") long expiryHours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expiryMillis = expiryHours * 3_600_000L;
  }

  public String generate(String username, String role) {
    Date now = new Date();
    return Jwts.builder()
        .subject(username)
        .claim("role", role)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expiryMillis))
        .signWith(key)
        .compact();
  }

  public String username(String token) {
    return Jwts.parser().verifyWith(key).build()
        .parseSignedClaims(token).getPayload().getSubject();
  }

  public String role(String token) {
    return Jwts.parser().verifyWith(key).build()
        .parseSignedClaims(token).getPayload().get("role", String.class);
  }
}
