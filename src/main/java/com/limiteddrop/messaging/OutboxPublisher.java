package com.limiteddrop.messaging;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.OutboxEvent;
import com.limiteddrop.persistence.OutboxEventRepository;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository events; private final RabbitTemplate rabbit; private final ReservationProperties properties; private final Clock clock;
    public OutboxPublisher(OutboxEventRepository events, RabbitTemplate rabbit, ReservationProperties properties, Clock clock) { this.events = events; this.rabbit = rabbit; this.properties = properties; this.clock = clock; }
    @Scheduled(fixedDelayString = "${app.outbox.publish-delay:PT2S}")
    public void publishPending() {
        events.findUnpublished(PageRequest.of(0, 100)).forEach(this::publishOne);
    }
    private void publishOne(OutboxEvent event) {
        try {
            CorrelationData correlation = new CorrelationData(event.getId());
            rabbit.convertAndSend(properties.getOutbox().getExchange(), event.getEventType(), event.getPayload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm.isAck()) events.markPublished(event.getId(), clock.instant()); else events.markFailed(event.getId());
        } catch (Exception ignored) {
            events.markFailed(event.getId());
        }
    }
}
