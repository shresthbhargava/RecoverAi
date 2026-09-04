package com.recoverai.agent;

import com.recoverai.agent.dto.DetectionResult;
import com.recoverai.entity.enums.EventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DetectionAgent {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = BigDecimal.valueOf(10000);
    private static final BigDecimal LOW_VALUE_THRESHOLD = BigDecimal.valueOf(300);

    public DetectionResult evaluate(EventType eventType, BigDecimal amount, String failureReason) {
        boolean atRisk = switch (eventType) {
            case PAYMENT_FAILED, CHECKOUT_ABANDONED, PAYMENT_PENDING_TOO_LONG,
                 PAYMENT_LINK_EXPIRED, SUBSCRIPTION_PAYMENT_FAILED -> true;
        };

        String category = categorize(eventType, failureReason);
        String severity = severityOf(amount);

        return new DetectionResult(atRisk, severity, category);
    }

    private String categorize(EventType eventType, String failureReason) {
        if (eventType == EventType.CHECKOUT_ABANDONED) return "CHECKOUT_ABANDONMENT";
        if (eventType == EventType.PAYMENT_LINK_EXPIRED) return "LINK_EXPIRY";
        if (eventType == EventType.SUBSCRIPTION_PAYMENT_FAILED) return "SUBSCRIPTION_FAILURE";

        if (failureReason != null) {
            String reason = failureReason.toUpperCase();
            if (reason.contains("TIMEOUT") || reason.contains("BANK")) return "TEMPORARY_PAYMENT_FAILURE";
            if (reason.contains("INSUFFICIENT")) return "INSUFFICIENT_FUNDS";
            if (reason.contains("DECLINED") || reason.contains("EXPIRED_CARD")) return "PAYMENT_METHOD_FAILURE";
        }
        return "UNCATEGORIZED_FAILURE";
    }

    private String severityOf(BigDecimal amount) {
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) return "HIGH";
        if (amount.compareTo(LOW_VALUE_THRESHOLD) <= 0) return "LOW";
        return "MEDIUM";
    }
}