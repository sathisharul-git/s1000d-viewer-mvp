package com.s1000Dorg.viewer.security;

import com.s1000Dorg.viewer.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String rolesClaim;

    public JwtService(SecurityProperties securityProperties) {
        this.signingKey = Keys.hmacShaKeyFor(normalizeSecret(securityProperties.getJwt().getSecret()));
        this.expirationSeconds = securityProperties.getJwt().getExpirationSeconds();
        this.rolesClaim = securityProperties.getClaimMapping().getRolesClaim();
    }

    public String generateToken(String username, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claims(Map.of(rolesClaim, List.copyOf(roles)))
            .signWith(signingKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(rolesClaim);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private byte[] normalizeSecret(String secret) {
        String normalizedSecret = Objects.requireNonNullElse(secret, "").trim();
        if (normalizedSecret.isBlank()) {
            throw new IllegalStateException("viewer.security.jwt.secret must be configured");
        }
        byte[] raw = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }

        byte[] padded = new byte[32];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = raw[i % raw.length];
        }
        return padded;
    }
}

