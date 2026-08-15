package com.limiteddrop.persistence;

import com.limiteddrop.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt asc")
    List<OutboxEvent> findUnpublished(Pageable pageable);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.publishedAt = :publishedAt, e.attempts = e.attempts + 1 where e.id = :id and e.publishedAt is null")
    int markPublished(@Param("id") String id, @Param("publishedAt") Instant publishedAt);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.attempts = e.attempts + 1 where e.id = :id and e.publishedAt is null")
    int markFailed(@Param("id") String id);
}
