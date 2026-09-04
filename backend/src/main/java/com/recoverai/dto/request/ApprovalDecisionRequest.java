package com.recoverai.dto.request;

import com.recoverai.entity.enums.RecoveryAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * approvedAction is optional for cases parked by an incentive-cap breach (we just
 * re-run the action the Strategy Agent already proposed). It is REQUIRED for cases
 * parked by ESCALATE_TO_MERCHANT, because "escalate" is not itself an executable
 * action — the whole point of escalating is that a human picks what happens next.
 */
public record ApprovalDecisionRequest(

        @NotBlank
        @Size(max = 120)
        String approvedBy,

        RecoveryAction approvedAction,

        @Size(max = 500)
        String note
) {
}