package com.limiteddrop.persistence;

import com.limiteddrop.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
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
    void returnsUnpublishedEventsOldestFirstWithPageLimit() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        OutboxEvent first = new OutboxEvent("first", "hold-1", "hold.created", "{}", now.minusSeconds(2));
        OutboxEvent second = new OutboxEvent("second", "hold-2", "hold.confirmed", "{}", now.minusSeconds(1));
        OutboxEvent published = new OutboxEvent("published", "hold-3", "hold.cancelled", "{}", now.minusSeconds(3));
        published.published(now);
        events.saveAllAndFlush(List.of(second, published, first));

        assertThat(events.findUnpublished(PageRequest.of(0, 1))).extracting(OutboxEvent::getId)
                .containsExactly("first");
        assertThat(events.findUnpublished(PageRequest.of(1, 1))).extracting(OutboxEvent::getId)
                .containsExactly("second");
    }

    @Test
    void marksPublishedAndFailedOnlyWhileUnpublished() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        events.saveAllAndFlush(List.of(
                new OutboxEvent("published", "hold-1", "hold.created", "{}", now),
                new OutboxEvent("failed", "hold-2", "hold.created", "{}", now)));

        assertThat(events.markPublished("published", now.plusSeconds(1))).isEqualTo(1);
        assertThat(events.markPublished("published", now.plusSeconds(2))).isZero();
        assertThat(events.markFailed("failed")).isEqualTo(1);
        assertThat(events.markFailed("failed")).isEqualTo(1);
        assertThat(events.markFailed("missing")).isZero();

        entityManager.clear();
        assertThat(events.findById("published").orElseThrow().getPublishedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(events.findById("failed").orElseThrow().getPublishedAt()).isNull();
    }
}
