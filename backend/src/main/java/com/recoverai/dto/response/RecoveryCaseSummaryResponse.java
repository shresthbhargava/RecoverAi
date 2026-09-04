package com.recoverai.dto.response;

import com.recoverai.entity.RecoveryCase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecoveryCaseSummaryResponse(
        UUID id,
        String customerName,
        BigDecimal amountAtRisk,
        String status,
        String diagnosis,
        BigDecimal diagnosisConfidence,
        String selectedStrategy,
        Integer attemptCount,
        Instant createdAt,
        Instant resolvedAt
) {
    public static RecoveryCaseSummaryResponse from(RecoveryCase c) {
        return new RecoveryCaseSummaryResponse(
                c.getId(),
                c.getCustomer() != null ? c.getCustomer().getName() : null,
                c.getAmountAtRisk(),
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getDiagnosis() != null ? c.getDiagnosis().name() : null,
                c.getDiagnosisConfidence(),
                c.getSelectedStrategy() != null ? c.getSelectedStrategy().name() : null,
                c.getAttemptCount(),
                c.getCreatedAt(),
                c.getResolvedAt()
        );
    }
}
