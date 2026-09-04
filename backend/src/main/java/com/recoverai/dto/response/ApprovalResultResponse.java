package com.recoverai.dto.response;

import com.recoverai.entity.enums.RecoveryAction;

import java.math.BigDecimal;
import java.util.UUID;

public record ApprovalResultResponse(
        UUID caseId,
        String decision,            // APPROVED / REJECTED
        RecoveryAction executedAction,
        String caseStatus,
        String attemptStatus,
        BigDecimal amountRecovered,
        String message
) {
}