package com.limiteddrop.messaging;

import com.limiteddrop.domain.OutboxEvent;
import com.limiteddrop.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxClaimService {
    private final OutboxEventRepository events;

    public OutboxClaimService(OutboxEventRepository events) { this.events = events; }

    @Transactional
    public List<OutboxEvent> claim(String owner, int batchSize, Instant now, Duration claimDuration) {
        List<OutboxEvent> claimed = events.findClaimableForUpdate(now, batchSize);
        if (claimed.isEmpty()) return claimed;
        List<String> ids = claimed.stream().map(OutboxEvent::getId).toList();
        int updated = events.claim(ids, owner, now.plus(claimDuration));
        if (updated != ids.size()) throw new IllegalStateException("Could not claim the complete outbox batch");
        return claimed;
    }

    @Transactional
    public void markPublished(String owner, List<String> ids, Instant publishedAt) {
        if (!ids.isEmpty()) events.markPublished(ids, owner, publishedAt);
    }

    @Transactional
    public void releaseForRetry(String owner, List<String> ids, Instant retryAt) {
        if (!ids.isEmpty()) events.releaseForRetry(ids, owner, retryAt);
    }
}
