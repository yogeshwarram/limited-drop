package com.limiteddrop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_idempotency_records")
public class AdminIdempotencyRecord {
    @Id private String id;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String operation;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "response_status", nullable = false) private int responseStatus;
    @Column(name = "response_json", nullable = false, columnDefinition = "json") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AdminIdempotencyRecord() { }
    public AdminIdempotencyRecord(String id, String actor, String operation, String idempotencyKey, String requestHash, int responseStatus, String responseJson, Instant createdAt) {
        this.id = id; this.actor = actor; this.operation = operation; this.idempotencyKey = idempotencyKey; this.requestHash = requestHash; this.responseStatus = responseStatus; this.responseJson = responseJson; this.createdAt = createdAt;
    }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
    public int getResponseStatus() { return responseStatus; }
}
