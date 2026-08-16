package com.limiteddrop.service;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.exception.ReservationBusyException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Keeps a hot drop from consuming every database connection in one replica. */
@Component
public class DropAdmissionController {
    private final ConcurrentHashMap<String, Semaphore> permits = new ConcurrentHashMap<>();
    private final ReservationProperties properties;

    public DropAdmissionController(ReservationProperties properties) { this.properties = properties; }

    public Permit acquire(String dropId) {
        Semaphore semaphore = permits.computeIfAbsent(dropId,
                ignored -> new Semaphore(properties.getReservations().getAdmissionPermits(), true));
        try {
            if (!semaphore.tryAcquire(properties.getReservations().getAdmissionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ReservationBusyException();
            }
            return semaphore::release;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ReservationBusyException();
        }
    }

    @FunctionalInterface
    public interface Permit extends AutoCloseable {
        @Override void close();
    }
}
