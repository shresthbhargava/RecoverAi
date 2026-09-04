package com.recoverai.dto.response;

import com.recoverai.entity.RecoveryCase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecoveryCaseDetailResponse(
        UUID id,
        UUID paymentId,
        String customerName,
        String customerEmail,
        Integer customerPastSuccessfulPayments,
        Integer customerPastFailedPayments,
        BigDecimal amountAtRisk,
        String status,
        String diagnosis,
        BigDecimal diagnosisConfidence,
        BigDecimal recoveryProbability,
        BigDecimal expectedRecoveryValue,
        BigDecimal recoveryCost,
        String selectedStrategy,
        Integer attemptCount,
        Instant createdAt,
        Instant resolvedAt,
        List<AgentDecisionResponse> agentDecisions,
        List<RecoveryAttemptResponse> attempts
) {
    public static RecoveryCaseDetailResponse from(
            RecoveryCase c,
            List<AgentDecisionResponse> decisions,
            List<RecoveryAttemptResponse> attempts
    ) {
        return new RecoveryCaseDetailResponse(
                c.getId(),
                c.getPayment().getId(),
                c.getCustomer().getName(),
                c.getCustomer().getEmail(),
                c.getCustomer().getTotalSuccessfulPayments(),
                c.getCustomer().getTotalFailedPayments(),
                c.getAmountAtRisk(),
                c.getStatus().name(),
                c.getDiagnosis() != null ? c.getDiagnosis().name() : null,
                c.getDiagnosisConfidence(),
                c.getRecoveryProbability(),
                c.getExpectedRecoveryValue(),
                c.getRecoveryCost(),
                c.getSelectedStrategy() != null ? c.getSelectedStrategy().name() : null,
                c.getAttemptCount(),
                c.getCreatedAt(),
                c.getResolvedAt(),
                decisions,
                attempts
        );
    }
}
