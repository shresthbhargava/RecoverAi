package com.recoverai.dto.response;

import com.recoverai.entity.PolicyRule;

import java.util.UUID;

public record PolicyRuleResponse(
        UUID id,
        String name,
        String ruleType,
        String value,
        Boolean enabled,
        String description
) {
    public static PolicyRuleResponse from(PolicyRule r) {
        return new PolicyRuleResponse(r.getId(), r.getName(), r.getRuleType(), r.getValue(), r.getEnabled(), r.getDescription());
    }
}
