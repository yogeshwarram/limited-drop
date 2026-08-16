package com.limiteddrop.persistence;

import com.limiteddrop.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    @Query(value = "select * from outbox_events where published_at is null and (claimed_until is null or claimed_until < :now) order by occurred_at limit :batchSize for update skip locked", nativeQuery = true)
    List<OutboxEvent> findClaimableForUpdate(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Query("update OutboxEvent e set e.claimOwner = :owner, e.claimedUntil = :claimedUntil where e.id in :ids and e.publishedAt is null")
    int claim(@Param("ids") List<String> ids, @Param("owner") String owner, @Param("claimedUntil") Instant claimedUntil);

    @Modifying
    @Query("update OutboxEvent e set e.publishedAt = :publishedAt, e.attempts = e.attempts + 1, e.claimOwner = null, e.claimedUntil = null where e.id in :ids and e.claimOwner = :owner and e.publishedAt is null")
    int markPublished(@Param("ids") List<String> ids, @Param("owner") String owner, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("update OutboxEvent e set e.attempts = e.attempts + 1, e.claimOwner = null, e.claimedUntil = :retryAt where e.id in :ids and e.claimOwner = :owner and e.publishedAt is null")
    int releaseForRetry(@Param("ids") List<String> ids, @Param("owner") String owner, @Param("retryAt") Instant retryAt);
}
