package com.recoverai.analytics;

import com.recoverai.dto.response.AnalyticsSummaryResponse;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every figure here is a live aggregate query against recovery_case /
 * recovery_attempt — nothing is precomputed or hardcoded, per the
 * "metrics must be calculated from actual processed records" rule.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public AnalyticsSummaryResponse getSummary() {
        long totalCases = recoveryCaseRepository.count();

        long recoverable = recoveryCaseRepository.countByStatus(CaseStatus.RECOVERED)
                + recoveryCaseRepository.countByStatus(CaseStatus.IN_PROGRESS)
                + recoveryCaseRepository.countByStatus(CaseStatus.OPEN);

        long recoveryAttempts = recoveryAttemptRepository.count();
        long successful = recoveryAttemptRepository.countByStatus(AttemptStatus.SUCCEEDED);
        long failed = recoveryAttemptRepository.countByStatus(AttemptStatus.FAILED);
        long blocked = recoveryAttemptRepository.countByStatus(AttemptStatus.BLOCKED);

        BigDecimal revenueAtRisk = recoveryCaseRepository.sumAmountAtRisk();
        BigDecimal revenueRecovered = recoveryCaseRepository.sumAmountRecovered();
        BigDecimal recoveryCost = recoveryCaseRepository.sumRecoveryCost();
        BigDecimal netRecovered = revenueRecovered.subtract(recoveryCost);

        BigDecimal recoveryRate = recoveryAttempts == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(successful)
                    .divide(BigDecimal.valueOf(recoveryAttempts), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

        return new AnalyticsSummaryResponse(
                totalCases,
                revenueAtRisk,
                recoverable,
                recoveryAttempts,
                successful,
                failed,
                blocked,
                revenueRecovered,
                recoveryRate,
                recoveryCost,
                netRecovered
        );
    }
}
