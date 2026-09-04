package com.recoverai.repository;

import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {

    Page<RecoveryCase> findByStatus(CaseStatus status, Pageable pageable);

    List<RecoveryCase> findByCustomerId(UUID customerId);

    @Query("select count(c) from RecoveryCase c where c.status = :status")
    long countByStatus(@Param("status") CaseStatus status);

    @Query("select coalesce(sum(c.amountAtRisk), 0) from RecoveryCase c")
    BigDecimal sumAmountAtRisk();

    @Query("select coalesce(sum(a.amountRecovered), 0) from RecoveryAttempt a where a.status = 'SUCCEEDED'")
    BigDecimal sumAmountRecovered();

    @Query("select coalesce(sum(c.recoveryCost), 0) from RecoveryCase c")
    BigDecimal sumRecoveryCost();
}
