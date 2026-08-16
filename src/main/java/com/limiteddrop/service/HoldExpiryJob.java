package com.limiteddrop.service;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.exception.DatabaseFailureClassifier;
import com.limiteddrop.observability.DependencyAvailabilityLogger;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class HoldExpiryJob {
    private final HoldExpiryService expiry; private final ReservationProperties properties; private final Clock clock;
    private final DependencyAvailabilityLogger dependencies;
    public HoldExpiryJob(HoldExpiryService expiry, ReservationProperties properties, Clock clock, DependencyAvailabilityLogger dependencies) {
        this.expiry = expiry; this.properties = properties; this.clock = clock; this.dependencies = dependencies;
    }
    @Scheduled(fixedDelayString = "${app.reservations.expiry-scan-delay:PT5S}", scheduler = "holdExpiryTaskScheduler")
    public void expireHolds() {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                expiry.expireNextBatch(clock.instant(), properties.getReservations().getExpiryBatchSize());
                dependencies.recovered("mysql");
                return;
            } catch (CannotAcquireLockException deadlock) {
                if (attempt == 2) {
                    dependencies.failed("mysql", deadlock);
                    return;
                }
                try { Thread.sleep(25L << attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            } catch (RuntimeException failure) {
                if (!DatabaseFailureClassifier.isUnavailable(failure)) throw failure;
                dependencies.failed("mysql", failure);
                return;
            }
        }
    }
}
