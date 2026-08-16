package com.limiteddrop.persistence;

import com.limiteddrop.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
class OutboxEventRepositoryTest {
    @Autowired OutboxEventRepository events;
    @PersistenceContext EntityManager entityManager;

    @Test
    void claimsOnlyAvailableUnpublishedEventsOldestFirst() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        OutboxEvent first = new OutboxEvent("first", "hold-1", "hold.created", "{}", now.minusSeconds(2));
        OutboxEvent second = new OutboxEvent("second", "hold-2", "hold.confirmed", "{}", now.minusSeconds(1));
        OutboxEvent published = new OutboxEvent("published", "hold-3", "hold.cancelled", "{}", now.minusSeconds(3));
        published.published(now);
        events.saveAllAndFlush(List.of(second, published, first));

        List<OutboxEvent> firstClaim = events.findClaimableForUpdate(now, 1);
        assertThat(firstClaim).extracting(OutboxEvent::getId).containsExactly("first");
        assertThat(events.claim(List.of("first"), "worker-1", now.plusSeconds(30))).isEqualTo(1);

        assertThat(events.findClaimableForUpdate(now, 10)).extracting(OutboxEvent::getId)
                .containsExactly("second");
        assertThat(events.findClaimableForUpdate(now.plusSeconds(31), 10)).extracting(OutboxEvent::getId)
                .containsExactly("first", "second");
    }

    @Test
    void bulkOutcomesRequireTheClaimOwnerAndReleaseRetries() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        events.saveAllAndFlush(List.of(
                new OutboxEvent("published", "hold-1", "hold.created", "{}", now),
                new OutboxEvent("failed", "hold-2", "hold.created", "{}", now)));

        assertThat(events.claim(List.of("published", "failed"), "worker-1", now.plusSeconds(30))).isEqualTo(2);
        assertThat(events.markPublished(List.of("published"), "other-worker", now.plusSeconds(1))).isZero();
        assertThat(events.markPublished(List.of("published"), "worker-1", now.plusSeconds(1))).isEqualTo(1);
        assertThat(events.markPublished(List.of("published"), "worker-1", now.plusSeconds(2))).isZero();
        assertThat(events.releaseForRetry(List.of("failed"), "other-worker", now.plusSeconds(5))).isZero();
        assertThat(events.releaseForRetry(List.of("failed"), "worker-1", now.plusSeconds(5))).isEqualTo(1);
        assertThat(events.releaseForRetry(List.of("failed"), "worker-1", now.plusSeconds(5))).isZero();

        entityManager.clear();
        assertThat(events.findById("published").orElseThrow().getPublishedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(events.findById("failed").orElseThrow().getPublishedAt()).isNull();
        assertThat(events.findById("failed").orElseThrow().getClaimOwner()).isNull();
        assertThat(events.findById("failed").orElseThrow().getClaimedUntil()).isEqualTo(now.plusSeconds(5));
    }
}
