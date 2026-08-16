package com.limiteddrop.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedDuplicateKeyLogFilterTest {
    private final ExpectedDuplicateKeyLogFilter filter = new ExpectedDuplicateKeyLogFilter();

    @Test
    void suppressesExpectedHoldIdempotencyRaceAtErrorLevel() {
        var event = event(Level.ERROR, "Duplicate entry 'drop-customer-key' for key 'holds.uq_hold_idempotency'");

        assertThat(filter.decide(event)).isEqualTo(FilterReply.DENY);
    }

    @Test
    void suppressesKnownTransientDatabaseErrors() {
        var event = event(Level.ERROR, "Deadlock found when trying to get lock; try restarting transaction");

        assertThat(filter.decide(event)).isEqualTo(FilterReply.DENY);
    }

    @Test
    void keepsUnknownDatabaseErrorsVisible() {
        var event = event(Level.ERROR, "Data truncation: invalid input");

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void keepsSameMessageFromAnotherLoggerVisible() {
        var event = event(Level.ERROR, "Duplicate entry 'x' for key 'holds.uq_hold_idempotency'");
        event.setLoggerName("com.limiteddrop.service.HoldService");

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void keepsNonErrorDuplicateMessagesVisible() {
        var event = event(Level.WARN, "Duplicate entry 'x' for key 'holds.uq_hold_idempotency'");

        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    private LoggingEvent event(Level level, String message) {
        var event = new LoggingEvent();
        event.setLevel(level);
        event.setLoggerName("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        event.setMessage(message);
        return event;
    }
}
