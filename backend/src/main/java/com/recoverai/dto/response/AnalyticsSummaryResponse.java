package com.recoverai.dto.response;

import java.math.BigDecimal;

public record AnalyticsSummaryResponse(
        long totalEventsProcessed,
        BigDecimal revenueAtRisk,
        long recoverableCases,
        long recoveryAttempts,
        long successfulRecoveries,
        long failedRecoveries,
        long blockedActions,
        BigDecimal revenueRecovered,
        BigDecimal recoveryRate,
        BigDecimal recoveryCost,
        BigDecimal netRecoveredRevenue
) {
}
