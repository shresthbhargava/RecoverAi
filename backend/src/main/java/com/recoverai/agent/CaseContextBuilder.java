package com.recoverai.agent;

import com.recoverai.agent.dto.CaseContext;
import com.recoverai.agent.dto.DetectionResult;
import com.recoverai.entity.Customer;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.repository.RecoveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseContextBuilder {

    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public CaseContext build(RecoveryCase recoveryCase) {
        Customer customer = recoveryCase.getCustomer();
        long priorAttempts = recoveryAttemptRepository.countByRecoveryCaseId(recoveryCase.getId());

        DetectionResult detection = new DetectionResult(
                true,
                severityFromAmount(recoveryCase.getAmountAtRisk()),
                categoryFromPayment(recoveryCase)
        );

        return new CaseContext(
                recoveryCase.getPayment().getId().toString(),
                recoveryCase.getAmountAtRisk(),
                recoveryCase.getPayment().getCurrency(),
                recoveryCase.getPayment().getEventType().name(),
                recoveryCase.getPayment().getFailureReason(),
                customer.getTotalSuccessfulPayments(),
                customer.getTotalFailedPayments(),
                customer.getLifetimeValue(),
                Boolean.TRUE.equals(customer.getOptedOut()),
                (int) priorAttempts,
                detection.severity(),
                detection.category()
        );
    }

    private String severityFromAmount(java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.valueOf(10000)) >= 0) return "HIGH";
        if (amount.compareTo(java.math.BigDecimal.valueOf(300)) <= 0) return "LOW";
        return "MEDIUM";
    }

    private String categoryFromPayment(RecoveryCase recoveryCase) {
        String reason = recoveryCase.getPayment().getFailureReason();
        if (reason == null) return "UNCATEGORIZED_FAILURE";
        String upper = reason.toUpperCase();
        if (upper.contains("TIMEOUT") || upper.contains("BANK")) return "TEMPORARY_PAYMENT_FAILURE";
        if (upper.contains("INSUFFICIENT")) return "INSUFFICIENT_FUNDS";
        return "UNCATEGORIZED_FAILURE";
    }
}