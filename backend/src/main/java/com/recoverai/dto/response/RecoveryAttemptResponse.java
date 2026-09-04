package com.recoverai.dto.response;

import com.recoverai.entity.RecoveryAttempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecoveryAttemptResponse(
        UUID id,
        Integer attemptNumber,
        String action,
        String status,
        Boolean isRealApiAction,
        Instant executedAt,
        String result,
        BigDecimal amountRecovered
) {
    public static RecoveryAttemptResponse from(RecoveryAttempt a) {
        return new RecoveryAttemptResponse(
                a.getId(),
                a.getAttemptNumber(),
                a.getAction().name(),
                a.getStatus().name(),
                a.getIsRealApiAction(),
                a.getExecutedAt(),
                a.getResult(),
                a.getAmountRecovered()
        );
    }
}
