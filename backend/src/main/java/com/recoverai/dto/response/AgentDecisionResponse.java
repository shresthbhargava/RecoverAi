package com.recoverai.dto.response;

import com.recoverai.entity.AgentDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentDecisionResponse(
        UUID id,
        String agentType,
        String inputSummary,
        String decision,
        BigDecimal confidence,
        List<String> reasoning,
        String llmModel,
        Integer latencyMs,
        Instant createdAt
) {
    public static AgentDecisionResponse from(AgentDecision d) {
        return new AgentDecisionResponse(
                d.getId(),
                d.getAgentType().name(),
                d.getInputSummary(),
                d.getDecision(),
                d.getConfidence(),
                d.getReasoning(),
                d.getLlmModel(),
                d.getLatencyMs(),
                d.getCreatedAt()
        );
    }
}
