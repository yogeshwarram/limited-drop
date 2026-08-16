package com.limiteddrop.messaging;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    @Mock OutboxClaimService claims;
    @Mock RabbitTemplate rabbit;

    @Test
    void releasesClaimWhenBrokerSendThrows() {
        ReservationProperties properties = properties();
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30)))).thenReturn(List.of(event("event-1")));
        doThrow(new RuntimeException("broker down")).when(rabbit)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        publisher(properties).publishPending();

        verify(claims).markPublished(anyString(), eq(List.of()), eq(NOW));
        verify(claims).releaseForRetry(anyString(), eq(List.of("event-1")), eq(NOW.plusSeconds(5)));
    }

    @Test
    void bulkMarksEventAfterBrokerAcknowledges() {
        ReservationProperties properties = properties();
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30)))).thenReturn(List.of(event("event-1")));
        acknowledgeSends(true);

        publisher(properties).publishPending();

        verify(claims).markPublished(anyString(), eq(List.of("event-1")), eq(NOW));
        verify(claims).releaseForRetry(anyString(), eq(List.of()), eq(NOW.plusSeconds(5)));
    }

    @Test
    void releasesClaimAfterBrokerRejects() {
        ReservationProperties properties = properties();
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30)))).thenReturn(List.of(event("event-rejected")));
        acknowledgeSends(false);

        publisher(properties).publishPending();

        verify(claims).markPublished(anyString(), eq(List.of()), eq(NOW));
        verify(claims).releaseForRetry(anyString(), eq(List.of("event-rejected")), eq(NOW.plusSeconds(5)));
    }

    @Test
    void sendsTheWholeBatchBeforeWaitingForConfirmations() {
        ReservationProperties properties = properties();
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30))))
                .thenReturn(List.of(event("first"), event("second")));
        AtomicReference<CorrelationData> first = new AtomicReference<>();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (correlation.getId().equals("first")) first.set(correlation);
            else {
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                first.get().getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        publisher(properties).publishPending();

        verify(rabbit, times(2)).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));
        verify(claims).markPublished(anyString(), eq(List.of("first", "second")), eq(NOW));
    }

    @Test
    void releasesUnconfirmedEventsAfterTheBatchTimeout() {
        ReservationProperties properties = properties();
        properties.getOutbox().setConfirmTimeout(Duration.ofMillis(1));
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30)))).thenReturn(List.of(event("timed-out")));

        publisher(properties).publishPending();

        verify(claims).releaseForRetry(anyString(), eq(List.of("timed-out")), eq(NOW.plusSeconds(5)));
    }

    @Test
    void doesNothingWhenThereAreNoClaimableEvents() {
        when(claims.claim(anyString(), eq(1000), eq(NOW), eq(Duration.ofSeconds(30)))).thenReturn(List.of());

        publisher(properties()).publishPending();

        verifyNoInteractions(rabbit);
        verify(claims, never()).markPublished(anyString(), anyList(), any());
        verify(claims, never()).releaseForRetry(anyString(), anyList(), any());
    }

    private OutboxPublisher publisher(ReservationProperties properties) {
        return new OutboxPublisher(claims, rabbit, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ReservationProperties properties() {
        ReservationProperties properties = new ReservationProperties();
        properties.getOutbox().setExchange("drop.events.test");
        return properties;
    }

    private OutboxEvent event(String id) { return new OutboxEvent(id, "hold-1", "hold.created", "{}", NOW); }

    private void acknowledgeSends(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));
    }
}
