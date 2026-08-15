package com.limiteddrop.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "holds")
public class Hold {
    @Id private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "drop_id") private Drop drop;
    @Column(name = "customer_id", nullable = false) private String customerId;
    @Column(nullable = false) private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private HoldState state;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "confirmation_idempotency_key") private String confirmationIdempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Version private long version;

    protected Hold() { }
    public Hold(String id, Drop drop, String customerId, int quantity, Instant expiresAt, String idempotencyKey, Instant createdAt) {
        this.id = id; this.drop = drop; this.customerId = customerId; this.quantity = quantity; this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey; this.createdAt = createdAt; this.state = HoldState.ACTIVE;
    }
    public void confirm(String key, Instant now) { this.state = HoldState.CONFIRMED; this.confirmedAt = now; this.confirmationIdempotencyKey = key; }
    public void cancel(Instant now) { this.state = HoldState.CANCELLED; this.cancelledAt = now; }
    public void expire() { this.state = HoldState.EXPIRED; }
    public String getId() { return id; }
    public Drop getDrop() { return drop; }
    public String getCustomerId() { return customerId; }
    public int getQuantity() { return quantity; }
    public HoldState getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
