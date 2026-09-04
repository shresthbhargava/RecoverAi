package com.recoverai.policy;

public record PolicyDecision(
        boolean allowed,
        boolean requiresMerchantApproval,
        String reason
) {
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, false, reason);
    }

    public static PolicyDecision block(String reason) {
        return new PolicyDecision(false, false, reason);
    }

    public static PolicyDecision requireApproval(String reason) {
        return new PolicyDecision(false, true, reason);
    }
}