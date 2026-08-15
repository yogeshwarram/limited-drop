package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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

        assertThat(new SecurityConfiguration().jwtDecoder(properties)).isNotNull();
    }
}
