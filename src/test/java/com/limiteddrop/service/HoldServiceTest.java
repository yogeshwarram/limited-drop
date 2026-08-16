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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.dao.DataIntegrityViolationException;
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
    @Mock DropMetadataCache metadataCache;
    @Mock DropAdmissionController admission;
    HoldService service;
    Drop drop;

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new HoldService(drops, holds, outbox, properties, clock, transactions, metadataCache, admission);
        drop = new Drop("drop-1", "Tiny drop", 3, NOW.minusSeconds(60), null, NOW);
        lenient().when(metadataCache.get("drop-1")).thenReturn(new DropMetadata("drop-1", "Tiny drop", 3, NOW.minusSeconds(60), null));
        lenient().when(admission.acquire(anyString())).thenReturn(() -> { });
        lenient().when(drops.getReferenceById(anyString())).thenReturn(drop);
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void createsHoldOnlyAfterAtomicInventoryReservation() {
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1")).thenReturn(Optional.empty());
        when(drops.reserveIfAvailable("drop-1", 2)).thenReturn(1);

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(2), "key-1");

        assertThat(result.hold().state()).isEqualTo(HoldState.ACTIVE);
        assertThat(result.hold().expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(drops).reserveIfAvailable("drop-1", 2);
        InOrder pressureOrder = inOrder(admission, metadataCache);
        pressureOrder.verify(admission).acquire("drop-1");
        pressureOrder.verify(metadataCache).get("drop-1");
        ArgumentCaptor<Hold> hold = ArgumentCaptor.forClass(Hold.class);
        verify(holds).save(hold.capture());
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

    @Test
    void rejectsReplayWhenQuantityChanges() {
        Hold existing = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key-1", NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create("drop-1", "customer-1", new CreateHoldRequest(2), "key-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency-Key was previously used with a different quantity");
        verifyNoInteractions(outbox);
        verifyNoInteractions(drops);
    }

    @Test
    void reportsInsufficientCapacityWhenDropIsOpen() {
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.empty());
        when(drops.reserveIfAvailable("drop-1", 2)).thenReturn(0);

        assertThatThrownBy(() -> service.create("drop-1", "customer-1", new CreateHoldRequest(2), "key-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Insufficient units remaining");
        verify(holds, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void reportsNotOpenWhenReservationIsAttemptedEarly() {
        Drop future = new Drop("future", "Future", 3, NOW.plusSeconds(60), null, NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("future", "customer-1", "key-1"))
                .thenReturn(Optional.empty());
        when(metadataCache.get("future")).thenReturn(new DropMetadata("future", "Future", 3, NOW.plusSeconds(60), null));

        assertThatThrownBy(() -> service.create("future", "customer-1", new CreateHoldRequest(1), "key-1"))
                .isInstanceOf(DropNotOpenException.class);
    }

    @Test
    void reportsMissingDropWhenReservationFailsForUnknownDrop() {
        when(metadataCache.get("missing")).thenThrow(new NotFoundException("Drop not found"));

        assertThatThrownBy(() -> service.create("missing", "customer-1", new CreateHoldRequest(1), "key-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Drop not found");
    }

    @Test
    void confirmIsStateIdempotentAndDoesNotPublishAgain() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key", NOW);
        hold.confirm("first-confirm", NOW.minusSeconds(1));
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        HoldResponse result = service.confirm("hold-1", "customer-1", "retry-confirm");

        assertThat(result.state()).isEqualTo(HoldState.CONFIRMED);
        assertThat(result.confirmedAt()).isEqualTo(NOW.minusSeconds(1));
        verifyNoInteractions(drops, outbox);
    }

    @Test
    void cannotConfirmCancelledHold() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key", NOW);
        hold.cancel(NOW.minusSeconds(1));
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.confirm("hold-1", "customer-1", "confirm-key"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only an active hold can be confirmed");
        verifyNoInteractions(drops, outbox);
    }

    @Test
    void cancellingExpiredHoldExpiresItAndReleasesUnits() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 2, NOW.minusSeconds(1), "key", NOW.minusSeconds(700));
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.cancel("hold-1", "customer-1"))
                .isInstanceOf(HoldExpiredException.class);

        assertThat(hold.getState()).isEqualTo(HoldState.EXPIRED);
        verify(drops).returnUnits("drop-1", 2);
        verify(outbox).record("hold.expired", hold, NOW);
    }

    @Test
    void expiryIsSuccessfulOnlyForExpiredActiveHolds() {
        Hold expired = new Hold("expired", drop, "customer-1", 2, NOW.minusSeconds(1), "key", NOW.minusSeconds(700));
        when(holds.findByIdForUpdate("expired")).thenReturn(Optional.of(expired));

        assertThat(service.expire("expired")).isTrue();
        assertThat(service.expire("expired")).isFalse();
        verify(drops, times(1)).returnUnits("drop-1", 2);
        verify(outbox, times(1)).record("hold.expired", expired, NOW);
    }

    @Test
    void expirySkipsMissingFutureAndAlreadyConfirmedHolds() {
        when(holds.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertThat(service.expire("missing")).isFalse();

        Hold future = new Hold("future", drop, "customer-1", 1, NOW.plusSeconds(1), "key", NOW);
        when(holds.findByIdForUpdate("future")).thenReturn(Optional.of(future));
        assertThat(service.expire("future")).isFalse();

        Hold confirmed = new Hold("confirmed", drop, "customer-1", 1, NOW.minusSeconds(1), "key", NOW);
        confirmed.confirm("confirm", NOW.minusSeconds(2));
        when(holds.findByIdForUpdate("confirmed")).thenReturn(Optional.of(confirmed));
        assertThat(service.expire("confirmed")).isFalse();

        verifyNoInteractions(drops, outbox);
    }

    @Test
    void getHidesAnotherCustomersHold() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key", NOW);
        when(holds.findById("hold-1")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.get("hold-1", "customer-2"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Hold not found");
    }

    @Test
    void rechecksIdempotencyInsideTransactionWhenConcurrentRequestWon() {
        Hold existing = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key-1", NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.of(existing));

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(1), "key-1");

        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(drops, outbox);
    }

    @Test
    void replaysAfterUniqueConstraintRace() {
        Hold existing = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key-1", NOW);
        doThrow(new DataIntegrityViolationException("duplicate")).when(transactions).execute(any(TransactionCallback.class));
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.of(existing));

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(1), "key-1");

        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(drops, outbox);
    }

    @Test
    void propagatesUniqueConstraintRaceWhenReplayCannotBeFound() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(transactions).execute(any(TransactionCallback.class));
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("drop-1", "customer-1", new CreateHoldRequest(1), "key-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replaysIdempotencyKeyFoundAfterInventoryWait() {
        Hold existing = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key-1", NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer-1", "key-1"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(drops.reserveIfAvailable("drop-1", 1)).thenReturn(0);
        when(holds.findIdempotencyKeyForUpdate("drop-1", "customer-1", "key-1")).thenReturn(Optional.of(existing));

        HoldCreation result = service.create("drop-1", "customer-1", new CreateHoldRequest(1), "key-1");

        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(outbox);
    }

    @Test
    void usesDropSpecificHoldDurationAndReportsUnknownDropAfterReservation() {
        Drop custom = new Drop("custom", "Custom", 3, NOW.minusSeconds(1), 42, NOW);
        when(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("custom", "customer-1", "key"))
                .thenReturn(Optional.empty());
        when(drops.reserveIfAvailable("custom", 1)).thenReturn(1);
        when(metadataCache.get("custom")).thenReturn(new DropMetadata("custom", "Custom", 3, NOW.minusSeconds(60), 120));
        when(drops.getReferenceById("custom")).thenReturn(custom);

        HoldCreation result = service.create("custom", "customer-1", new CreateHoldRequest(1), "key");
        assertThat(result.hold().expiresAt()).isEqualTo(NOW.plusSeconds(120));

        when(metadataCache.get("missing")).thenThrow(new NotFoundException("Drop not found"));
        assertThatThrownBy(() -> service.create("missing", "customer-1", new CreateHoldRequest(1), "key"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void confirmsActiveHoldAndRejectsCancellingConfirmedHold() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, NOW.plusSeconds(600), "key", NOW);
        when(holds.findByIdForUpdate("hold-1")).thenReturn(Optional.of(hold));
        assertThat(service.confirm("hold-1", "customer-1", "confirm-key").state()).isEqualTo(HoldState.CONFIRMED);
        verify(outbox).record("hold.confirmed", hold, NOW);

        assertThatThrownBy(() -> service.cancel("hold-1", "customer-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only an active hold can be cancelled");
    }

    @Test
    void reportsMissingHoldForReadAndLockedTransitions() {
        when(holds.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("missing", "customer-1")).isInstanceOf(NotFoundException.class);
        when(holds.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm("missing", "customer-1", "key")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel("missing", "customer-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsOwnedHold() {
        Hold hold = new Hold("hold-1", drop, "customer-1", 2, NOW.plusSeconds(600), "key", NOW);
        when(holds.findById("hold-1")).thenReturn(Optional.of(hold));

        assertThat(service.get("hold-1", "customer-1").id()).isEqualTo("hold-1");
    }
}
