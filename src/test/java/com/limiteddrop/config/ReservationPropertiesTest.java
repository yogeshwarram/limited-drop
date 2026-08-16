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
        assertThat(properties.getOutbox().getPublishDelay()).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.getOutbox().getConfirmTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getOutbox().getClaimDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getOutbox().getRetryDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getOutbox().getBatchSize()).isEqualTo(1000);
        assertThat(properties.getOutbox().isAuditQueueEnabled()).isFalse();
        assertThat(properties.getOutbox().getExchange()).isEqualTo("drop.events");
        assertThat(properties.getSeed().isEnabled()).isTrue();

        properties.getReservations().setDefaultHoldDuration(Duration.ofMinutes(2));
        properties.getReservations().setExpiryScanDelay(Duration.ofSeconds(8));
        properties.getReservations().setExpiryBatchSize(12);
        properties.getOutbox().setPublishDelay(Duration.ofSeconds(4));
        properties.getOutbox().setConfirmTimeout(Duration.ofSeconds(7));
        properties.getOutbox().setClaimDuration(Duration.ofMinutes(1));
        properties.getOutbox().setRetryDelay(Duration.ofSeconds(9));
        properties.getOutbox().setBatchSize(50);
        properties.getOutbox().setExchange("events");
        properties.getOutbox().setAuditQueueEnabled(true);
        properties.getSeed().setEnabled(false);
        properties.getSecurity().setIssuerUri("issuer");
        properties.getSecurity().setJwkSetUri("jwks");
        properties.getSecurity().setHmacSecret("secret");
        properties.getSecurity().setDevTokenEndpointEnabled(true);

        assertThat(properties.getReservations().getDefaultHoldDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getReservations().getExpiryScanDelay()).isEqualTo(Duration.ofSeconds(8));
        assertThat(properties.getReservations().getExpiryBatchSize()).isEqualTo(12);
        assertThat(properties.getOutbox().getPublishDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.getOutbox().getConfirmTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(properties.getOutbox().getClaimDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.getOutbox().getRetryDelay()).isEqualTo(Duration.ofSeconds(9));
        assertThat(properties.getOutbox().getBatchSize()).isEqualTo(50);
        assertThat(properties.getOutbox().getExchange()).isEqualTo("events");
        assertThat(properties.getOutbox().isAuditQueueEnabled()).isTrue();
        assertThat(properties.getSeed().isEnabled()).isFalse();
        assertThat(properties.getSecurity().getIssuerUri()).isEqualTo("issuer");
        assertThat(properties.getSecurity().getJwkSetUri()).isEqualTo("jwks");
        assertThat(properties.getSecurity().getHmacSecret()).isEqualTo("secret");
        assertThat(properties.getSecurity().isDevTokenEndpointEnabled()).isTrue();
    }
}
