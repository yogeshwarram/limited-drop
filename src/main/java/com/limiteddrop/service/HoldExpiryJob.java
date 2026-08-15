package com.limiteddrop.service;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.persistence.HoldRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class HoldExpiryJob {
    private final HoldRepository holds; private final HoldService holdService; private final ReservationProperties properties; private final Clock clock;
    public HoldExpiryJob(HoldRepository holds, HoldService holdService, ReservationProperties properties, Clock clock) { this.holds = holds; this.holdService = holdService; this.properties = properties; this.clock = clock; }
    @Scheduled(fixedDelayString = "${app.reservations.expiry-scan-delay:PT5S}")
    public void expireHolds() {
        holds.findExpiredIds(HoldState.ACTIVE, clock.instant(), PageRequest.of(0, properties.getReservations().getExpiryBatchSize()))
                .forEach(holdService::expire);
    }
}
