package com.recoverai.repository;

import com.recoverai.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByRecoveryCaseIdOrderByTimestampAsc(UUID recoveryCaseId);

    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
