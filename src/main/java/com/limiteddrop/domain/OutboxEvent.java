package com.limiteddrop.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private String id;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "json") private String payload;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(nullable = false) private int attempts;
    protected OutboxEvent() { }
    public OutboxEvent(String id, String aggregateId, String eventType, String payload, Instant occurredAt) {
        this.id = id; this.aggregateId = aggregateId; this.eventType = eventType; this.payload = payload; this.occurredAt = occurredAt;
    }
    public void published(Instant when) { this.publishedAt = when; this.attempts++; }
    public void failedAttempt() { this.attempts++; }
    public String getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
