package com.limiteddrop.persistence;

import com.limiteddrop.domain.AdminAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, String> {
    Page<AdminAudit> findByDropIdOrderByOccurredAtDesc(String dropId, Pageable pageable);
}
