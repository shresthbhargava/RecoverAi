package com.recoverai.agent.dto;

import java.math.BigDecimal;
import java.util.List;

public record StrategyLlmOutput(
        String recommendedAction,
        BigDecimal recoveryProbability,
        List<String> reasoning
) {
}