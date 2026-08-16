package com.limiteddrop.persistence;

import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, String> {
    Optional<Hold> findByDrop_IdAndCustomerIdAndIdempotencyKey(String dropId, String customerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h join fetch h.drop where h.drop.id = :dropId and h.customerId = :customerId and h.idempotencyKey = :idempotencyKey")
    Optional<Hold> findIdempotencyKeyForUpdate(@Param("dropId") String dropId, @Param("customerId") String customerId, @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h join fetch h.drop where h.id = :id")
    Optional<Hold> findByIdForUpdate(@Param("id") String id);

    @Query("select h.id from Hold h where h.state = :state and h.expiresAt <= :now order by h.expiresAt asc")
    List<String> findExpiredIds(@Param("state") HoldState state, @Param("now") Instant now, Pageable pageable);

    @Query(value = "select id from holds where state = :state and expires_at <= :now order by expires_at asc limit :batchSize for update skip locked", nativeQuery = true)
    List<String> claimExpiredIdsForUpdate(@Param("state") String state, @Param("now") Instant now, @Param("batchSize") int batchSize);
}
