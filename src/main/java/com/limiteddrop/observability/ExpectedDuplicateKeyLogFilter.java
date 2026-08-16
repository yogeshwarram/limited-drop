package com.limiteddrop.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Same-key reservation races are resolved by the unique constraint and replay
 * path in HoldService. Hibernate logs that expected constraint race before the
 * service can handle it, so keep it out of ERROR telemetry without hiding
 * unrelated persistence failures.
 */
public final class ExpectedDuplicateKeyLogFilter extends AbstractMatcherFilter<ILoggingEvent> {
    private static final String HIBERNATE_SQL_EXCEPTION_LOGGER = "org.hibernate.engine.jdbc.spi.SqlExceptionHelper";
    private static final String HOLD_IDEMPOTENCY_CONSTRAINT = "holds.uq_hold_idempotency";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLevel() == Level.ERROR
                && HIBERNATE_SQL_EXCEPTION_LOGGER.equals(event.getLoggerName())
                && event.getFormattedMessage() != null
                && event.getFormattedMessage().contains(HOLD_IDEMPOTENCY_CONSTRAINT)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
