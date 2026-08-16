package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigurationTest {
    @Test
    void createsHmacDecoderForValidSecret() {
        ReservationProperties properties = new ReservationProperties();
        properties.getSecurity().setHmacSecret("test-secret-with-at-least-thirty-two-bytes");

        JwtDecoder decoder = new SecurityConfiguration().jwtDecoder(properties);

        assertThat(decoder).isNotNull();
    }

    @Test
    void hmacDecoderAcceptsOnlyTheConfiguredAudience() {
        ReservationProperties properties = new ReservationProperties();
        properties.getSecurity().setHmacSecret("test-secret-with-at-least-thirty-two-bytes");
        properties.getSecurity().setAudience("limited-drop-api");
        JwtDecoder decoder = new SecurityConfiguration().jwtDecoder(properties);

        String token = new DemoTokenService(properties, Clock.systemUTC()).issue("alice");
        assertThat(decoder.decode(token).getAudience()).containsExactly("limited-drop-api");

        properties.getSecurity().setAudience("another-api");
        String wrongAudience = new DemoTokenService(properties, Clock.systemUTC()).issue("alice");
        assertThatThrownBy(() -> decoder.decode(wrongAudience)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsMissingOrShortSecurityConfiguration() {
        ReservationProperties missing = new ReservationProperties();
        assertThatThrownBy(() -> new SecurityConfiguration().jwtDecoder(missing))
                .isInstanceOf(IllegalStateException.class);

        ReservationProperties shortSecret = new ReservationProperties();
        shortSecret.getSecurity().setHmacSecret("too-short");
        assertThatThrownBy(() -> new SecurityConfiguration().jwtDecoder(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(">=32 byte");
    }

    @Test
    void createsDecoderFromDirectJwkSetUri() {
        ReservationProperties properties = new ReservationProperties();
        properties.getSecurity().setJwkSetUri("http://localhost:1/jwks");
        properties.getSecurity().setExpectedIssuer("https://issuer.example");

        assertThat(new SecurityConfiguration().jwtDecoder(properties)).isNotNull();
    }

    @Test
    void directJwkSetRequiresAnExpectedIssuer() {
        ReservationProperties properties = new ReservationProperties();
        properties.getSecurity().setJwkSetUri("http://localhost:1/jwks");

        assertThatThrownBy(() -> new SecurityConfiguration().jwtDecoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_EXPECTED_ISSUER");
    }
}
