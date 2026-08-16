package com.limiteddrop.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DependencyAvailabilityLogger {
    private static final Logger log = LoggerFactory.getLogger(DependencyAvailabilityLogger.class);
    private final ConcurrentHashMap<String, AtomicBoolean> unavailable = new ConcurrentHashMap<>();
    private final MeterRegistry meters;

    public DependencyAvailabilityLogger(MeterRegistry meters) { this.meters = meters; }

    public void failed(String dependency, Throwable failure) {
        meters.counter("limiteddrop.dependency.failures", "dependency", dependency).increment();
        if (unavailable.computeIfAbsent(dependency, ignored -> new AtomicBoolean()).compareAndSet(false, true)) {
            log.warn("{} is temporarily unavailable: {}", dependency, failure.getMessage());
        }
    }

    public void recovered(String dependency) {
        AtomicBoolean state = unavailable.get(dependency);
        if (state != null && state.compareAndSet(true, false)) {
            meters.counter("limiteddrop.dependency.recoveries", "dependency", dependency).increment();
            log.info("{} recovered", dependency);
        }
    }
}
