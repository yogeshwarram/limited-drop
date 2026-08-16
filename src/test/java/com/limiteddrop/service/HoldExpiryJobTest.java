package com.limiteddrop.service;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.observability.DependencyAvailabilityLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldExpiryJobTest {
    @Mock HoldExpiryService expiry;
    @Mock DependencyAvailabilityLogger dependencies;

    @Test
    void expiresOneBoundedClaimedBatch() {
        ReservationProperties properties = new ReservationProperties();
        properties.getReservations().setExpiryBatchSize(7);
        Instant now = Instant.parse("2026-08-15T10:00:00Z");

        job(properties, now).expireHolds();

        verify(expiry).expireNextBatch(now, 7);
        verify(dependencies).recovered("mysql");
    }

    @Test
    void retriesDeadlocksBeforeReportingFailure() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        when(expiry.expireNextBatch(eq(now), anyInt()))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("deadlock"))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("deadlock"))
                .thenReturn(1);

        job(new ReservationProperties(), now).expireHolds();

        verify(expiry, times(3)).expireNextBatch(eq(now), anyInt());
        verify(dependencies).recovered("mysql");
        verify(dependencies, never()).failed(eq("mysql"), any());
    }

    @Test
    void containsTransactionCreationFailuresAtSchedulerBoundary() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        var unavailable = new org.springframework.transaction.CannotCreateTransactionException("database down");
        when(expiry.expireNextBatch(eq(now), anyInt())).thenThrow(unavailable);

        job(new ReservationProperties(), now).expireHolds();

        verify(dependencies).failed("mysql", unavailable);
    }

    private HoldExpiryJob job(ReservationProperties properties, Instant now) {
        return new HoldExpiryJob(expiry, properties, Clock.fixed(now, ZoneOffset.UTC), dependencies);
    }
}
