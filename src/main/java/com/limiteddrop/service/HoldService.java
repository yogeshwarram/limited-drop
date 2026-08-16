package com.limiteddrop.service;

import com.limiteddrop.request.CreateHoldRequest;
import com.limiteddrop.response.HoldResponse;
import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.persistence.HoldRepository;
import com.limiteddrop.exception.ConflictException;
import com.limiteddrop.exception.DropNotOpenException;
import com.limiteddrop.exception.HoldExpiredException;
import com.limiteddrop.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class HoldService {
    private final DropRepository drops;
    private final HoldRepository holds;
    private final OutboxService outbox;
    private final ReservationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final DropMetadataCache metadataCache;
    private final DropAdmissionController admission;

    public HoldService(DropRepository drops, HoldRepository holds, OutboxService outbox, ReservationProperties properties, Clock clock,
                       TransactionTemplate transactions, DropMetadataCache metadataCache, DropAdmissionController admission) {
        this.drops = drops; this.holds = holds; this.outbox = outbox; this.properties = properties; this.clock = clock;
        this.transactions = transactions; this.metadataCache = metadataCache; this.admission = admission;
        this.transactions.setTimeout(properties.getReservations().getTransactionTimeoutSeconds());
    }

    public HoldCreation create(String dropId, String customerId, CreateHoldRequest request, String idempotencyKey) {
        try (DropAdmissionController.Permit ignored = admission.acquire(dropId)) {
            DropMetadata metadata = metadataCache.get(dropId);
            try {
                return Objects.requireNonNull(transactions.execute(status -> createInTransaction(dropId, customerId, request, idempotencyKey, metadata)));
            } catch (DataIntegrityViolationException duplicate) {
                Hold replay = holds.findByDrop_IdAndCustomerIdAndIdempotencyKey(dropId, customerId, idempotencyKey)
                        .orElseThrow(() -> duplicate);
                return replay(replay, request.quantity());
            }
        }
    }

    private HoldCreation createInTransaction(String dropId, String customerId, CreateHoldRequest request, String idempotencyKey, DropMetadata metadata) {
        Hold existing = holds.findByDrop_IdAndCustomerIdAndIdempotencyKey(dropId, customerId, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, request.quantity());
        Instant now = clock.instant();
        if (metadata.opensAt().isAfter(now)) throw new DropNotOpenException();
        if (drops.reserveIfAvailable(dropId, request.quantity()) == 0) {
            // A same-key request may have committed while this transaction waited on the inventory row.
            Hold replay = holds.findIdempotencyKeyForUpdate(dropId, customerId, idempotencyKey).orElse(null);
            if (replay != null) return replay(replay, request.quantity());
            throw new ConflictException("Insufficient units remaining");
        }
        int seconds = metadata.holdDurationSeconds() == null ? Math.toIntExact(properties.getReservations().getDefaultHoldDuration().toSeconds()) : metadata.holdDurationSeconds();
        Drop drop = drops.getReferenceById(dropId);
        Hold hold = new Hold(UUID.randomUUID().toString(), drop, customerId, request.quantity(), now.plusSeconds(seconds), idempotencyKey, now);
        holds.save(hold);
        outbox.record("hold.created", hold, now);
        return new HoldCreation(response(hold), false);
    }

    @Transactional
    public HoldResponse get(String holdId, String customerId) { return response(requireOwned(holdId, customerId, false)); }

    @Transactional(rollbackOn = Exception.class, dontRollbackOn = HoldExpiredException.class)
    public HoldResponse confirm(String holdId, String customerId, String idempotencyKey) {
        Hold hold = requireOwned(holdId, customerId, true);
        if (hold.getState() == HoldState.CONFIRMED) return response(hold);
        if (hold.getState() != HoldState.ACTIVE) throw new ConflictException("Only an active hold can be confirmed");
        Instant now = clock.instant();
        if (!hold.getExpiresAt().isAfter(now)) {
            expireLocked(hold, now);
            throw new HoldExpiredException();
        }
        hold.confirm(idempotencyKey, now);
        outbox.record("hold.confirmed", hold, now);
        return response(hold);
    }

    @Transactional(rollbackOn = Exception.class, dontRollbackOn = HoldExpiredException.class)
    public HoldResponse cancel(String holdId, String customerId) {
        Hold hold = requireOwned(holdId, customerId, true);
        if (hold.getState() == HoldState.CANCELLED) return response(hold);
        if (hold.getState() != HoldState.ACTIVE) throw new ConflictException("Only an active hold can be cancelled");
        Instant now = clock.instant();
        if (!hold.getExpiresAt().isAfter(now)) {
            expireLocked(hold, now);
            throw new HoldExpiredException();
        }
        hold.cancel(now);
        drops.returnUnits(hold.getDrop().getId(), hold.getQuantity());
        outbox.record("hold.cancelled", hold, now);
        return response(hold);
    }

    @Transactional
    public boolean expire(String holdId) {
        Hold hold = holds.findByIdForUpdate(holdId).orElse(null);
        if (hold == null || hold.getState() != HoldState.ACTIVE || hold.getExpiresAt().isAfter(clock.instant())) return false;
        expireLocked(hold, clock.instant());
        return true;
    }

    private void expireLocked(Hold hold, Instant now) {
        hold.expire();
        drops.returnUnits(hold.getDrop().getId(), hold.getQuantity());
        outbox.record("hold.expired", hold, now);
    }

    private Hold requireOwned(String holdId, String customerId, boolean locked) {
        Hold hold = (locked ? holds.findByIdForUpdate(holdId) : holds.findById(holdId)).orElseThrow(() -> new NotFoundException("Hold not found"));
        if (!hold.getCustomerId().equals(customerId)) throw new NotFoundException("Hold not found");
        return hold;
    }
    private HoldCreation replay(Hold hold, int quantity) {
        if (hold.getQuantity() != quantity) throw new ConflictException("Idempotency-Key was previously used with a different quantity");
        return new HoldCreation(response(hold), true);
    }
    public static HoldResponse response(Hold hold) { return new HoldResponse(hold.getId(), hold.getDrop().getId(), hold.getQuantity(), hold.getState(), hold.getExpiresAt(), hold.getCreatedAt(), hold.getConfirmedAt(), hold.getCancelledAt()); }
}
