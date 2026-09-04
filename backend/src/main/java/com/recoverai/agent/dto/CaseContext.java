package com.recoverai.agent.dto;

import java.math.BigDecimal;

public record CaseContext(
        String paymentRef,
        BigDecimal amount,
        String currency,
        String eventType,
        String failureReason,
        int customerSuccessfulPayments,
        int customerFailedPayments,
        BigDecimal customerLifetimeValue,
        boolean customerOptedOut,
        int priorRecoveryAttempts,
        String detectionSeverity,
        String detectionCategory
) {
}