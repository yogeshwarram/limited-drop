package com.limiteddrop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.limiteddrop.domain.AdminIdempotencyRecord;
import com.limiteddrop.domain.AdminAudit;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.AdminAuditRepository;
import com.limiteddrop.persistence.AdminIdempotencyRepository;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.request.BulkCapacityAdjustmentRequest;
import com.limiteddrop.request.CapacityAdjustmentRequest;
import com.limiteddrop.request.CreateDropRequest;
import com.limiteddrop.response.DropResponse;
import com.limiteddrop.exception.ConflictException;
import com.limiteddrop.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDropServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    @Mock DropRepository drops;
    @Mock AdminAuditRepository audits;
    @Mock AdminIdempotencyRepository idempotency;
    @Mock OutboxService outbox;
    @Mock TransactionTemplate transactions;
    private AdminDropService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        service = new AdminDropService(drops, audits, idempotency, outbox, mapper,
                Clock.fixed(NOW, ZoneOffset.UTC), transactions);
    }

    @Test
    void createsDropWithAuditAndEvent() {
        CreateDropRequest request = new CreateDropRequest("Concert", 10, NOW, 600);
        when(drops.saveAndFlush(any(Drop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("admin", "create-key", request);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().title()).isEqualTo("Concert");
        assertThat(result.body().availableUnits()).isEqualTo(10);
        verify(audits).save(any());
        verify(outbox).record(eq("drop.created"), anyString(), anyMap(), eq(NOW));
        verify(idempotency).save(any(AdminIdempotencyRecord.class));
    }

    @Test
    void replaysSameKeyAndRejectsChangedRequest() {
        CreateDropRequest request = new CreateDropRequest("Concert", 10, NOW, 600);
        when(drops.saveAndFlush(any(Drop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.create("admin", "same-key", request);
        var captured = org.mockito.ArgumentCaptor.forClass(AdminIdempotencyRecord.class);
        verify(idempotency).save(captured.capture());

        when(idempotency.findByActorAndOperationAndIdempotencyKey("admin", "create-drop", "same-key"))
                .thenReturn(Optional.of(captured.getValue()));
        var replay = service.create("admin", "same-key", request);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.body().title()).isEqualTo("Concert");
        verify(drops, times(1)).saveAndFlush(any(Drop.class));

        assertThatThrownBy(() -> service.create("admin", "same-key", new CreateDropRequest("Changed", 10, NOW, 600)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addsCapacityToBothTotalAndAvailableUnderLock() {
        Drop drop = new Drop("drop-1", "Concert", 10, NOW, null, NOW);
        when(drops.findByIdForUpdate("drop-1")).thenReturn(Optional.of(drop));
        when(drops.saveAndFlush(drop)).thenReturn(drop);

        var result = service.addCapacity("admin", "adjust-key", "drop-1", new CapacityAdjustmentRequest(5, "extra allocation"));

        assertThat(result.body()).extracting(DropResponse::totalUnits, DropResponse::availableUnits).containsExactly(15, 15);
        verify(drops).findByIdForUpdate("drop-1");
        verify(audits).save(any());
        verify(outbox).record(eq("drop.capacity.adjusted"), eq("drop-1"), anyMap(), eq(NOW));
    }

    @Test
    void rejectsDuplicateTargetsAndUnknownDropsInBulk() {
        var duplicate = new BulkCapacityAdjustmentRequest(List.of(
                new BulkCapacityAdjustmentRequest.CapacityAdjustmentItem("drop-1", 1, "one"),
                new BulkCapacityAdjustmentRequest.CapacityAdjustmentItem("drop-1", 2, "two")));
        assertThatThrownBy(() -> service.addCapacityBulk("admin", "bulk-key", duplicate)).isInstanceOf(ConflictException.class);

        var missing = new BulkCapacityAdjustmentRequest(List.of(
                new BulkCapacityAdjustmentRequest.CapacityAdjustmentItem("missing", 1, "one")));
        when(drops.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addCapacityBulk("admin", "bulk-key-2", missing)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsPerDropAuditHistory() {
        when(drops.existsById("drop-1")).thenReturn(true);
        when(audits.findByDropIdOrderByOccurredAtDesc("drop-1", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new AdminAudit("audit-1", "drop-1", "admin", "capacity.adjusted", "release", 10, 10, 12, 12, NOW))));

        var page = service.audit("drop-1", PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(audit -> {
            assertThat(audit.id()).isEqualTo("audit-1");
            assertThat(audit.actor()).isEqualTo("admin");
            assertThat(audit.afterAvailableUnits()).isEqualTo(12);
        });
    }
}
