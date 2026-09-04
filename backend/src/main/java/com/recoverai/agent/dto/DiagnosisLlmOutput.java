package com.recoverai.agent.dto;

import java.math.BigDecimal;
import java.util.List;

public record DiagnosisLlmOutput(
        String diagnosis,
        BigDecimal confidence,
        List<String> reasoning
) {
}