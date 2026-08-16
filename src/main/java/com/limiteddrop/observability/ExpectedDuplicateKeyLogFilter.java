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
    private static final String TRANSACTION_LOGGER_PREFIX = "org.springframework.transaction";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLevel() != Level.ERROR || event.getFormattedMessage() == null) return FilterReply.NEUTRAL;
        String message = event.getFormattedMessage();
        if (HIBERNATE_SQL_EXCEPTION_LOGGER.equals(event.getLoggerName())
                && (message.contains(HOLD_IDEMPOTENCY_CONSTRAINT)
                || message.startsWith("Communications link failure")
                || message.startsWith("HikariPool-1 - Connection is not available")
                || message.startsWith("Deadlock found when trying to get lock")
                || message.startsWith("Server shutdown in progress"))) return FilterReply.DENY;
        if (event.getLoggerName().startsWith(TRANSACTION_LOGGER_PREFIX)
                && message.startsWith("Application exception overridden by rollback exception")) return FilterReply.DENY;
        return FilterReply.NEUTRAL;
    }
}
