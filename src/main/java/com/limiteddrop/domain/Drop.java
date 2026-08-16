package com.limiteddrop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "drops")
public class Drop {
    @Id private String id;
    @Column(nullable = false) private String title;
    @Column(name = "total_units", nullable = false) private int totalUnits;
    @Column(name = "available_units", nullable = false) private int availableUnits;
    @Column(name = "opens_at", nullable = false) private Instant opensAt;
    @Column(name = "hold_duration_seconds") private Integer holdDurationSeconds;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Drop() { }
    public Drop(String id, String title, int totalUnits, Instant opensAt, Integer holdDurationSeconds, Instant createdAt) {
        this.id = id; this.title = title; this.totalUnits = totalUnits; this.availableUnits = totalUnits;
        this.opensAt = opensAt; this.holdDurationSeconds = holdDurationSeconds; this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getTotalUnits() { return totalUnits; }
    public int getAvailableUnits() { return availableUnits; }
    public Instant getOpensAt() { return opensAt; }
    public Integer getHoldDurationSeconds() { return holdDurationSeconds; }
    public void addCapacity(int quantity) {
        int adjustedTotal = Math.addExact(this.totalUnits, quantity);
        int adjustedAvailable = Math.addExact(this.availableUnits, quantity);
        this.totalUnits = adjustedTotal;
        this.availableUnits = adjustedAvailable;
    }
}
