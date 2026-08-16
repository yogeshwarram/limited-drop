package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class DemoTokenService {
    private final ReservationProperties properties;
    private final Clock clock;
    public DemoTokenService(ReservationProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public String issue(String subject) { return issue(subject, List.of()); }
    public String issue(String subject, List<String> scopes) {
        Instant now = clock.instant();
        var builder = Jwts.builder().subject(subject).audience().add(properties.getSecurity().getAudience()).and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)));
        if (scopes != null && !scopes.isEmpty()) builder.claim("scope", String.join(" ", scopes));
        return builder
                .signWith(Keys.hmacShaKeyFor(properties.getSecurity().getHmacSecret().getBytes(StandardCharsets.UTF_8))).compact();
    }
}
