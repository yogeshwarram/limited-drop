package com.limiteddrop.persistence;

import com.limiteddrop.domain.AdminIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminIdempotencyRepository extends JpaRepository<AdminIdempotencyRecord, String> {
    Optional<AdminIdempotencyRecord> findByActorAndOperationAndIdempotencyKey(String actor, String operation, String idempotencyKey);
}
