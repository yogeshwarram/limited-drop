package com.limiteddrop.persistence;

import com.limiteddrop.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OutboxClaimConcurrencyTest {
    @Autowired OutboxEventRepository events;
    @Autowired TransactionTemplate transactions;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingPublisherDoesNotBlockOnRowsLockedByAnotherClaimTransaction() throws Exception {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        transactions.executeWithoutResult(status -> events.saveAllAndFlush(List.of(
                new OutboxEvent("first", "hold-1", "hold.created", "{}", now.minusSeconds(1)),
                new OutboxEvent("second", "hold-2", "hold.created", "{}", now))));

        CountDownLatch firstRowLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstClaim = executor.submit(() -> transactions.execute(status -> {
                String id = events.findClaimableForUpdate(now.plusSeconds(1), 1).getFirst().getId();
                firstRowLocked.countDown();
                try {
                    if (!releaseFirstTransaction.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test lock was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return id;
            }));

            assertThat(firstRowLocked.await(2, TimeUnit.SECONDS)).isTrue();
            var secondClaim = executor.submit(() -> transactions.execute(status ->
                    events.findClaimableForUpdate(now.plusSeconds(1), 2)));

            // H2 does not refill an ordered LIMIT window after skipping its first locked row.
            // It can still verify the safety property here: the competing claim returns instead of blocking.
            assertThat(secondClaim.get(2, TimeUnit.SECONDS)).isEmpty();
            releaseFirstTransaction.countDown();
            assertThat(firstClaim.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }
}
