package com.limiteddrop.messaging;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.OutboxEvent;
import com.limiteddrop.exception.DatabaseFailureClassifier;
import com.limiteddrop.observability.DependencyAvailabilityLogger;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxClaimService claims; private final RabbitTemplate rabbit; private final ReservationProperties properties; private final Clock clock;
    private final DependencyAvailabilityLogger dependencies;
    private final String owner = UUID.randomUUID().toString();
    public OutboxPublisher(OutboxClaimService claims, RabbitTemplate rabbit, ReservationProperties properties, Clock clock, DependencyAvailabilityLogger dependencies) { this.claims = claims; this.rabbit = rabbit; this.properties = properties; this.clock = clock; this.dependencies = dependencies; }
    @Scheduled(fixedDelayString = "${app.outbox.publish-delay:PT0.25S}", scheduler = "outboxTaskScheduler")
    public void publishPending() {
        try {
            publish();
            dependencies.recovered("mysql");
        } catch (RuntimeException failure) {
            if (!DatabaseFailureClassifier.isUnavailable(failure)) throw failure;
            dependencies.failed("mysql", failure);
        }
    }

    private void publish() {
        Instant now = clock.instant();
        List<OutboxEvent> batch = claims.claim(owner, properties.getOutbox().getBatchSize(), now, properties.getOutbox().getClaimDuration());
        if (batch.isEmpty()) return;

        List<Dispatch> dispatched = new ArrayList<>(batch.size());
        List<String> failed = new ArrayList<>();
        for (OutboxEvent event : batch) {
            try {
                CorrelationData correlation = new CorrelationData(event.getId());
                rabbit.convertAndSend(properties.getOutbox().getExchange(), event.getEventType(), event.getPayload(), correlation);
                dispatched.add(new Dispatch(event.getId(), correlation.getFuture()));
            } catch (Exception sendFailure) {
                failed.add(event.getId());
                dependencies.failed("rabbitmq", sendFailure);
            }
        }

        waitForConfirmations(dispatched);
        List<String> published = new ArrayList<>();
        for (Dispatch dispatch : dispatched) {
            if (acknowledged(dispatch.confirm())) published.add(dispatch.id());
            else failed.add(dispatch.id());
        }
        claims.markPublished(owner, published, clock.instant());
        claims.releaseForRetry(owner, failed, clock.instant().plus(properties.getOutbox().getRetryDelay()));
        if (!published.isEmpty() && failed.isEmpty()) dependencies.recovered("rabbitmq");
    }

    private void waitForConfirmations(List<Dispatch> dispatched) {
        CompletableFuture<?>[] settled = dispatched.stream()
                .map(dispatch -> dispatch.confirm().handle((confirm, failure) -> null))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(settled).get(Math.max(1, properties.getOutbox().getConfirmTimeout().toMillis()), TimeUnit.MILLISECONDS);
        } catch (Exception timeoutOrInterruption) {
            if (timeoutOrInterruption instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private boolean acknowledged(CompletableFuture<CorrelationData.Confirm> confirmation) {
        if (!confirmation.isDone() || confirmation.isCompletedExceptionally() || confirmation.isCancelled()) return false;
        CorrelationData.Confirm confirm = confirmation.getNow(null);
        return confirm != null && confirm.isAck();
    }

    private record Dispatch(String id, CompletableFuture<CorrelationData.Confirm> confirm) { }
}
