package com.limiteddrop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_audit")
public class AdminAudit {
    @Id private String id;
    @Column(name = "drop_id", nullable = false) private String dropId;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String operation;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "before_total_units") private Integer beforeTotalUnits;
    @Column(name = "before_available_units") private Integer beforeAvailableUnits;
    @Column(name = "after_total_units") private Integer afterTotalUnits;
    @Column(name = "after_available_units") private Integer afterAvailableUnits;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected AdminAudit() { }
    public AdminAudit(String id, String dropId, String actor, String operation, String reason, Integer beforeTotalUnits, Integer beforeAvailableUnits, Integer afterTotalUnits, Integer afterAvailableUnits, Instant occurredAt) {
        this.id = id; this.dropId = dropId; this.actor = actor; this.operation = operation; this.reason = reason; this.beforeTotalUnits = beforeTotalUnits; this.beforeAvailableUnits = beforeAvailableUnits; this.afterTotalUnits = afterTotalUnits; this.afterAvailableUnits = afterAvailableUnits; this.occurredAt = occurredAt;
    }
    public String getId() { return id; }
    public String getDropId() { return dropId; }
    public String getActor() { return actor; }
    public String getOperation() { return operation; }
    public String getReason() { return reason; }
    public Integer getBeforeTotalUnits() { return beforeTotalUnits; }
    public Integer getBeforeAvailableUnits() { return beforeAvailableUnits; }
    public Integer getAfterTotalUnits() { return afterTotalUnits; }
    public Integer getAfterAvailableUnits() { return afterAvailableUnits; }
    public Instant getOccurredAt() { return occurredAt; }
}
