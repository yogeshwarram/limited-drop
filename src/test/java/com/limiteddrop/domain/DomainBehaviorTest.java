package com.limiteddrop.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainBehaviorTest {
    @Test
    void holdTransitionsPopulateOnlyTheirRelevantTimestamps() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = new Drop("drop", "Drop", 4, now, null, now);
        Hold hold = new Hold("hold", drop, "alice", 2, now.plusSeconds(60), "key", now);
        assertThat(hold.getState()).isEqualTo(HoldState.ACTIVE);
        assertThat(hold.getIdempotencyKey()).isEqualTo("key");
        assertThat(hold.getConfirmedAt()).isNull();
        assertThat(hold.getCancelledAt()).isNull();

        hold.confirm("confirm-key", now.plusSeconds(1));
        assertThat(hold.getState()).isEqualTo(HoldState.CONFIRMED);
        assertThat(hold.getConfirmedAt()).isEqualTo(now.plusSeconds(1));

        Hold cancelled = new Hold("cancelled", drop, "alice", 1, now.plusSeconds(60), "key-2", now);
        cancelled.cancel(now.plusSeconds(2));
        assertThat(cancelled.getState()).isEqualTo(HoldState.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(now.plusSeconds(2));

        cancelled.expire();
        assertThat(cancelled.getState()).isEqualTo(HoldState.EXPIRED);
    }

    @Test
    void outboxEventTracksAttemptAndPublishedState() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        OutboxEvent event = new OutboxEvent("event", "hold", "hold.created", "{}", now);
        assertThat(event.getPublishedAt()).isNull();
        event.failedAttempt();
        event.published(now.plusSeconds(1));
        assertThat(event.getPublishedAt()).isEqualTo(now.plusSeconds(1));
    }
}
