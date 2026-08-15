package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTokenServiceTest {
    @Test
    void issuesTokenWithExpectedSubjectAndOneHourLifetime() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        ReservationProperties properties = new ReservationProperties();
        String secret = "test-secret-with-at-least-thirty-two-bytes";
        properties.getSecurity().setHmacSecret(secret);

        String token = new DemoTokenService(properties, Clock.fixed(now, ZoneOffset.UTC)).issue("alice");
        var claims = Jwts.parser().clock(() -> Date.from(now))
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(now);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(now.plusSeconds(3600));
    }
}
