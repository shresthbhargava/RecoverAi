package com.recoverai.repository;

import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, UUID> {

    List<RecoveryAttempt> findByRecoveryCaseIdOrderByAttemptNumberAsc(UUID recoveryCaseId);

    /** Used by ApprovalService to find the attempt row parked by the escalation gate. */
    Optional<RecoveryAttempt> findFirstByRecoveryCaseIdAndStatusOrderByAttemptNumberDesc(
            UUID recoveryCaseId, AttemptStatus status);

    /**
     * The webhook join key. An inbound Razorpay event carries order_xxx / plink_xxx, never
     * our UUIDs, so this is the only route from a callback back to a RecoveryCase.
     *
     * Newest-first because external_ref is indexed but not unique: a retried case can
     * legitimately produce several orders, and a late webhook for an old order must not
     * overwrite the result of a newer attempt.
     */
    Optional<RecoveryAttempt> findFirstByExternalRefOrderByExecutedAtDesc(String externalRef);

    long countByRecoveryCaseId(UUID recoveryCaseId);

    @Query("select count(a) from RecoveryAttempt a where a.status = :status")
    long countByStatus(AttemptStatus status);
}
