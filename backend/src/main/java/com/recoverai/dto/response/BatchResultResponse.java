package com.recoverai.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BatchResultResponse(
        UUID batchId,
        int transactionsAnalyzed,
        BigDecimal revenueAtRisk,
        int recoveryActionsProposed,
        int actionsBlocked,
        int successfulRecoveries,
        BigDecimal revenueRecovered,
        BigDecimal recoveryCost,
        BigDecimal netRecoveredRevenue
) {
}
