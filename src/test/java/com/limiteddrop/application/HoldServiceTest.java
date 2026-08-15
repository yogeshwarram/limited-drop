package com.limiteddrop.application;

import com.limiteddrop.api.CreateHoldRequest;
import com.limiteddrop.api.HoldResponse;
import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.persistence.HoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    @Mock DropRepository drops;
    @Mock HoldRepository holds;
    @Mock OutboxService outbox;
    @Mock TransactionTemplate transactions;
    HoldService service;
    Drop drop;

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new HoldService(drops, holds, outbox, properties, clock, transactions);
        drop = new Drop("drop-1", "Tiny drop", 3, NOW.minusSeconds(60), null, NOW);
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void createsHoldOnlyAfterAtomicInventoryReservation() {
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1")).thenReturn(Optional.empty());
        when(drops.reserveIfAvailable("drop-1", 2)).thenReturn(1);
        when(drops.findById("drop-1")).thenReturn(Optional.of(drop));

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(2), "key-1");

        assertThat(result.hold().state()).isEqualTo(HoldState.ACTIVE);
        assertThat(result.hold().expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(drops).reserveIfAvailable("drop-1", 2);
        ArgumentCaptor<Hold> hold = ArgumentCaptor.forClass(Hold.class);
        verify(holds).saveAndFlush(hold.capture());
        assertThat(hold.getValue().getQuantity()).isEqualTo(2);
        verify(outbox).record(eq("hold.created"), eq(hold.getValue()), eq(NOW));
    }

    @Test
    void replaysSameIdempotencyKeyWithoutReservingAgain() {
        Hold existing = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key-1", NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1")).thenReturn(Optional.of(existing));

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(1), "key-1");

        assertThat(result.hold().id()).isEqualTo("hold-1");
        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(outbox);
        verify(drops, never()).reserveIfAvailable(anyString(), anyInt());
    }

    @Test
    void cancellationReturnsExactlyHeldUnitsOnce() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 2, NOW.plusSeconds(600), "key", NOW);
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        HoldResponse cancelled = service.cancel("hold-1", "customer-1");
        HoldResponse replay = service.cancel("hold-1", "customer-1");

        assertThat(cancelled.state()).isEqualTo(HoldState.CANCELLED);
        assertThat(replay.state()).isEqualTo(HoldState.CANCELLED);
        verify(drops, times(1)).returnUnits("drop-1", 2);
        verify(outbox, times(1)).record(eq("hold.cancelled"), eq(hold), eq(NOW));
    }

    @Test
    void confirmAfterExpiryReleasesInventoryAndReportsConflict() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 2, NOW.minusSeconds(1), "key", NOW.minusSeconds(700));
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm("hold-1", "customer-1", "confirm-key"))
                .isInstanceOf(HoldExpiredException.class);

        assertThat(hold.getState()).isEqualTo(HoldState.EXPIRED);
        verify(drops).returnUnits("drop-1", 2);
        verify(outbox).record(eq("hold.expired"), eq(hold), eq(NOW));
    }
}
