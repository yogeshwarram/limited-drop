package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class DemoTokenService {
    private final ReservationProperties properties;
    private final Clock clock;
    public DemoTokenService(ReservationProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public String issue(String subject) {
        Instant now = clock.instant();
        return Jwts.builder().subject(subject).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecurity().getHmacSecret().getBytes(StandardCharsets.UTF_8))).compact();
    }
}
