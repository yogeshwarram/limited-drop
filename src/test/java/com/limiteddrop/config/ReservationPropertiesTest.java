package com.limiteddrop.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationPropertiesTest {
    @Test
    void exposesDefaultsAndAllConfigurationSetters() {
        ReservationProperties properties = new ReservationProperties();
        assertThat(properties.getReservations().getDefaultHoldDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getReservations().getExpiryScanDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getReservations().getExpiryBatchSize()).isEqualTo(100);
        assertThat(properties.getOutbox().getPublishDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getOutbox().getExchange()).isEqualTo("drop.events");
        assertThat(properties.getSeed().isEnabled()).isTrue();

        properties.getReservations().setDefaultHoldDuration(Duration.ofMinutes(2));
        properties.getReservations().setExpiryScanDelay(Duration.ofSeconds(8));
        properties.getReservations().setExpiryBatchSize(12);
        properties.getOutbox().setPublishDelay(Duration.ofSeconds(4));
        properties.getOutbox().setExchange("events");
        properties.getSeed().setEnabled(false);
        properties.getSecurity().setIssuerUri("issuer");
        properties.getSecurity().setJwkSetUri("jwks");
        properties.getSecurity().setHmacSecret("secret");
        properties.getSecurity().setDevTokenEndpointEnabled(true);

        assertThat(properties.getReservations().getDefaultHoldDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getReservations().getExpiryScanDelay()).isEqualTo(Duration.ofSeconds(8));
        assertThat(properties.getReservations().getExpiryBatchSize()).isEqualTo(12);
        assertThat(properties.getOutbox().getPublishDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.getOutbox().getExchange()).isEqualTo("events");
        assertThat(properties.getSeed().isEnabled()).isFalse();
        assertThat(properties.getSecurity().getIssuerUri()).isEqualTo("issuer");
        assertThat(properties.getSecurity().getJwkSetUri()).isEqualTo("jwks");
        assertThat(properties.getSecurity().getHmacSecret()).isEqualTo("secret");
        assertThat(properties.getSecurity().isDevTokenEndpointEnabled()).isTrue();
    }
}
