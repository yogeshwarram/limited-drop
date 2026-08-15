package com.limiteddrop.application;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.persistence.HoldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldExpiryJobTest {
    @Mock HoldRepository holds;
    @Mock HoldService holdService;

    @Test
    void expiresOnlyTheBoundedCandidatePage() {
        ReservationProperties properties = new ReservationProperties();
        properties.getReservations().setExpiryBatchSize(7);
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        when(holds.findExpiredIds(HoldState.ACTIVE, now, PageRequest.of(0, 7)))
                .thenReturn(List.of("one", "two"));

        new HoldExpiryJob(holds, holdService, properties, Clock.fixed(now, ZoneOffset.UTC)).expireHolds();

        verify(holdService).expire("one");
        verify(holdService).expire("two");
        verifyNoMoreInteractions(holdService);
    }

    @Test
    void doesNothingWhenThereAreNoExpiredCandidates() {
        ReservationProperties properties = new ReservationProperties();
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        when(holds.findExpiredIds(eq(HoldState.ACTIVE), eq(now), any(PageRequest.class))).thenReturn(List.of());

        new HoldExpiryJob(holds, holdService, properties, Clock.fixed(now, ZoneOffset.UTC)).expireHolds();

        verifyNoInteractions(holdService);
    }
}
