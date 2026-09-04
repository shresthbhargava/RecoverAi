package com.recoverai.agent.dto;

public record DetectionResult(
        boolean revenueAtRisk,
        String severity,      // LOW / MEDIUM / HIGH
        String category
) {
}