package com.limiteddrop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limiteddrop.domain.AdminAudit;
import com.limiteddrop.domain.AdminIdempotencyRecord;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.exception.ConflictException;
import com.limiteddrop.exception.NotFoundException;
import com.limiteddrop.persistence.AdminAuditRepository;
import com.limiteddrop.persistence.AdminIdempotencyRepository;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.request.BulkCapacityAdjustmentRequest;
import com.limiteddrop.request.BulkCreateDropsRequest;
import com.limiteddrop.request.CapacityAdjustmentRequest;
import com.limiteddrop.request.CreateDropRequest;
import com.limiteddrop.response.AdminAuditResponse;
import com.limiteddrop.response.AdminBulkCapacityResponse;
import com.limiteddrop.response.AdminBulkDropsResponse;
import com.limiteddrop.response.DropResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class AdminDropService {
    public static final int MAX_BATCH_SIZE = 100;
    private final DropRepository drops;
    private final AdminAuditRepository audits;
    private final AdminIdempotencyRepository idempotency;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AdminDropService(DropRepository drops, AdminAuditRepository audits, AdminIdempotencyRepository idempotency,
                            OutboxService outbox, ObjectMapper objectMapper, Clock clock, TransactionTemplate transactions) {
        this.drops = drops; this.audits = audits; this.idempotency = idempotency; this.outbox = outbox;
        this.objectMapper = objectMapper; this.clock = clock; this.transactions = transactions;
    }

    public AdminCommandResult<DropResponse> create(String actor, String key, CreateDropRequest request) {
        return execute(actor, "create-drop", key, request, 201, DropResponse.class,
                () -> createDrop(actor, request));
    }

    public AdminCommandResult<AdminBulkDropsResponse> createBulk(String actor, String key, BulkCreateDropsRequest request) {
        return execute(actor, "create-drops-bulk", key, request, 201, AdminBulkDropsResponse.class,
                () -> createDrops(actor, request.drops()));
    }

    public AdminCommandResult<DropResponse> addCapacity(String actor, String key, String dropId, CapacityAdjustmentRequest request) {
        return execute(actor, "adjust-capacity:" + dropId, key, request, 200, DropResponse.class,
                () -> adjustOne(actor, dropId, request.quantity(), request.reason()));
    }

    public AdminCommandResult<AdminBulkCapacityResponse> addCapacityBulk(String actor, String key, BulkCapacityAdjustmentRequest request) {
        return execute(actor, "adjust-capacity-bulk", key, request, 200, AdminBulkCapacityResponse.class,
                () -> adjustMany(actor, request.adjustments()));
    }

    public Page<AdminAuditResponse> audit(String dropId, Pageable pageable) {
        if (!drops.existsById(dropId)) throw new NotFoundException("Drop not found");
        return audits.findByDropIdOrderByOccurredAtDesc(dropId, pageable).map(this::auditResponse);
    }

    private DropResponse createDrop(String actor, CreateDropRequest request) {
        Instant now = clock.instant();
        Drop drop = new Drop(UUID.randomUUID().toString(), request.title(), request.totalUnits(), request.opensAt(), request.holdDurationSeconds(), now);
        drops.saveAndFlush(drop);
        recordAudit(actor, drop, "created", "drop created", null, null, drop.getTotalUnits(), drop.getAvailableUnits(), now);
        return response(drop);
    }

    private AdminBulkDropsResponse createDrops(String actor, List<CreateDropRequest> requests) {
        List<DropResponse> result = new ArrayList<>();
        for (CreateDropRequest request : requests) result.add(createDrop(actor, request));
        return new AdminBulkDropsResponse(result);
    }

    private DropResponse adjustOne(String actor, String dropId, int quantity, String reason) {
        Drop drop = drops.findByIdForUpdate(dropId).orElseThrow(() -> new NotFoundException("Drop not found"));
        return adjustLocked(actor, drop, quantity, reason);
    }

    private AdminBulkCapacityResponse adjustMany(String actor, List<BulkCapacityAdjustmentRequest.CapacityAdjustmentItem> requests) {
        Map<String, BulkCapacityAdjustmentRequest.CapacityAdjustmentItem> byDrop = new LinkedHashMap<>();
        for (var item : requests) if (byDrop.put(item.dropId(), item) != null) throw new ConflictException("Duplicate drop in capacity batch: " + item.dropId());
        Map<String, DropResponse> responses = new LinkedHashMap<>();
        byDrop.keySet().stream().sorted(Comparator.naturalOrder()).forEach(dropId -> {
            var item = byDrop.get(dropId);
            Drop drop = drops.findByIdForUpdate(dropId).orElseThrow(() -> new NotFoundException("Drop not found: " + dropId));
            responses.put(dropId, adjustLocked(actor, drop, item.quantity(), item.reason()));
        });
        return new AdminBulkCapacityResponse(byDrop.keySet().stream().map(responses::get).toList());
    }

    private DropResponse adjustLocked(String actor, Drop drop, int quantity, String reason) {
        Instant now = clock.instant();
        int beforeTotal = drop.getTotalUnits();
        int beforeAvailable = drop.getAvailableUnits();
        drop.addCapacity(quantity);
        drops.saveAndFlush(drop);
        recordAudit(actor, drop, "capacity.adjusted", reason, beforeTotal, beforeAvailable, drop.getTotalUnits(), drop.getAvailableUnits(), now);
        return response(drop);
    }

    private void recordAudit(String actor, Drop drop, String operation, String reason, Integer beforeTotal, Integer beforeAvailable,
                             Integer afterTotal, Integer afterAvailable, Instant occurredAt) {
        audits.save(new AdminAudit(UUID.randomUUID().toString(), drop.getId(), actor, operation, reason, beforeTotal, beforeAvailable, afterTotal, afterAvailable, occurredAt));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString()); event.put("dropId", drop.getId()); event.put("actor", actor);
        event.put("operation", operation); event.put("reason", reason); event.put("beforeTotalUnits", beforeTotal);
        event.put("beforeAvailableUnits", beforeAvailable); event.put("afterTotalUnits", afterTotal); event.put("afterAvailableUnits", afterAvailable);
        event.put("occurredAt", occurredAt.toString());
        outbox.record("drop." + operation, drop.getId(), event, occurredAt);
    }

    private <T> AdminCommandResult<T> execute(String actor, String operation, String key, Object request, int status, Class<T> type, Supplier<T> work) {
        String hash = hash(request);
        AdminIdempotencyRecord existing = idempotency.findByActorAndOperationAndIdempotencyKey(actor, operation, key).orElse(null);
        if (existing != null) return replay(existing, hash, type);
        try {
            return Objects.requireNonNull(transactions.execute(transactionStatus -> {
                AdminIdempotencyRecord inside = idempotency.findByActorAndOperationAndIdempotencyKey(actor, operation, key).orElse(null);
                if (inside != null) return replay(inside, hash, type);
                T body = work.get();
                try {
                    String response = objectMapper.writeValueAsString(body);
                    idempotency.save(new AdminIdempotencyRecord(UUID.randomUUID().toString(), actor, operation, key, hash, status, response, clock.instant()));
                    return new AdminCommandResult<>(body, status, false);
                } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot store admin idempotency response", e); }
            }));
        } catch (DataIntegrityViolationException duplicate) {
            AdminIdempotencyRecord winner = idempotency.findByActorAndOperationAndIdempotencyKey(actor, operation, key).orElseThrow(() -> duplicate);
            return replay(winner, hash, type);
        }
    }

    private <T> AdminCommandResult<T> replay(AdminIdempotencyRecord record, String hash, Class<T> type) {
        if (!MessageDigest.isEqual(record.getRequestHash().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)))
            throw new ConflictException("Idempotency-Key was previously used with a different request");
        try { return new AdminCommandResult<>(objectMapper.readValue(record.getResponseJson(), type), record.getResponseStatus(), true); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read stored admin response", e); }
    }

    private String hash(Object request) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request))); }
        catch (Exception e) { throw new IllegalStateException("Cannot hash admin request", e); }
    }
    private String hex(byte[] bytes) { StringBuilder value = new StringBuilder(); for (byte b : bytes) value.append(String.format("%02x", b)); return value.toString(); }
    private DropResponse response(Drop drop) { return new DropResponse(drop.getId(), drop.getTitle(), drop.getTotalUnits(), drop.getAvailableUnits(), drop.getOpensAt(), drop.getHoldDurationSeconds()); }
    private AdminAuditResponse auditResponse(AdminAudit audit) { return new AdminAuditResponse(audit.getId(), audit.getDropId(), audit.getActor(), audit.getOperation(), audit.getReason(), audit.getBeforeTotalUnits(), audit.getBeforeAvailableUnits(), audit.getAfterTotalUnits(), audit.getAfterAvailableUnits(), audit.getOccurredAt()); }

    public record AdminCommandResult<T>(T body, int status, boolean replay) { }
}
