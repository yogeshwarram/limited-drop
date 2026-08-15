package com.limiteddrop.messaging;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.OutboxEvent;
import com.limiteddrop.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    @Mock OutboxEventRepository events;
    @Mock RabbitTemplate rabbit;

    @Test
    void marksEventFailedWhenBrokerSendThrows() {
        ReservationProperties properties = new ReservationProperties();
        properties.getOutbox().setExchange("drop.events.test");
        OutboxEvent event = new OutboxEvent("event-1", "hold-1", "hold.created", "{}", Instant.parse("2026-08-15T10:00:00Z"));
        when(events.findUnpublished(PageRequest.of(0, 100))).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down")).when(rabbit)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        new OutboxPublisher(events, rabbit, properties, Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC))
                .publishPending();

        verify(events).markFailed("event-1");
        verify(events, never()).markPublished(anyString(), any());
    }

    @Test
    void marksEventPublishedAfterBrokerAcknowledges() throws Exception {
        ReservationProperties properties = new ReservationProperties();
        properties.getOutbox().setExchange("drop.events.test");
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        OutboxEvent event = new OutboxEvent("event-1", "hold-1", "hold.created", "{}", now);
        when(events.findUnpublished(PageRequest.of(0, 100))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        new OutboxPublisher(events, rabbit, properties, Clock.fixed(now, ZoneOffset.UTC)).publishPending();

        verify(events).markPublished("event-1", now);
        verify(events, never()).markFailed(anyString());
    }

    @Test
    void marksEventFailedAfterBrokerRejects() throws Exception {
        ReservationProperties properties = new ReservationProperties();
        OutboxEvent event = new OutboxEvent("event-rejected", "hold-1", "hold.created", "{}", Instant.now());
        when(events.findUnpublished(PageRequest.of(0, 100))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        new OutboxPublisher(events, rabbit, properties, Clock.systemUTC()).publishPending();

        verify(events).markFailed("event-rejected");
        verify(events, never()).markPublished(anyString(), any());
    }

    @Test
    void doesNothingWhenThereAreNoPendingEvents() {
        when(events.findUnpublished(PageRequest.of(0, 100))).thenReturn(List.of());

        new OutboxPublisher(events, mock(RabbitTemplate.class), new ReservationProperties(), Clock.systemUTC())
                .publishPending();

        verifyNoInteractions(rabbit);
        verify(events).findUnpublished(PageRequest.of(0, 100));
    }
}
