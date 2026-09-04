package com.recoverai.policy;

import com.recoverai.entity.Customer;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.service.PolicyRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class PolicyEngine {

    private final PolicyRuleService policyRuleService;

    public PolicyDecision evaluate(RecoveryCase recoveryCase, Customer customer, RecoveryAction proposedAction,
                                   BigDecimal proposedIncentiveAmount) {

        if (proposedAction == RecoveryAction.NO_ACTION) {
            return PolicyDecision.allow("NO_ACTION requires no policy check");
        }

        if (policyRuleService.getBoolean("STOP_AFTER_CUSTOMER_OPTOUT", true) && Boolean.TRUE.equals(customer.getOptedOut())) {
            return PolicyDecision.block("Customer has opted out of contact");
        }

        int maxAttempts = policyRuleService.getInt("MAX_RECOVERY_ATTEMPTS", 3);
        if (recoveryCase.getAttemptCount() >= maxAttempts) {
            return PolicyDecision.block("Maximum recovery attempts (" + maxAttempts + ") exceeded");
        }

        int minIntervalMinutes = policyRuleService.getInt("MIN_RETRY_INTERVAL_MINUTES", 30);
        if (recoveryCase.getLastContactAt() != null) {
            long minutesSinceLastContact = ChronoUnit.MINUTES.between(recoveryCase.getLastContactAt(), Instant.now());
            if (minutesSinceLastContact < minIntervalMinutes) {
                return PolicyDecision.block(
                        "Minimum retry interval not yet elapsed (" + minutesSinceLastContact + "/" + minIntervalMinutes + " min)"
                );
            }
        }

        if (isCustomerFacingAction(proposedAction)) {
            int noContactAfterHour = policyRuleService.getInt("NO_CUSTOMER_CONTACT_AFTER", 21);
            int currentHour = LocalTime.now(ZoneId.systemDefault()).getHour();
            if (currentHour >= noContactAfterHour) {
                return PolicyDecision.block(
                        "Outside allowed contact window (current hour " + currentHour + ", cutoff " + noContactAfterHour + ")"
                );
            }
        }

        if (proposedAction == RecoveryAction.OFFER_SMALL_INCENTIVE) {
            BigDecimal maxAutoIncentive = policyRuleService.getDecimal("MAX_AUTO_INCENTIVE", BigDecimal.valueOf(500));
            if (proposedIncentiveAmount != null && proposedIncentiveAmount.compareTo(maxAutoIncentive) > 0) {
                return PolicyDecision.requireApproval(
                        "Incentive amount (" + proposedIncentiveAmount + ") exceeds auto-approval limit (" + maxAutoIncentive + ")"
                );
            }

            BigDecimal maxDiscountPct = policyRuleService.getDecimal("MAX_DISCOUNT_PERCENTAGE", BigDecimal.valueOf(10));
            if (proposedIncentiveAmount != null && recoveryCase.getAmountAtRisk().signum() > 0) {
                BigDecimal impliedPct = proposedIncentiveAmount
                        .divide(recoveryCase.getAmountAtRisk(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (impliedPct.compareTo(maxDiscountPct) > 0) {
                    return PolicyDecision.block("Implied discount (" + impliedPct + "%) exceeds max allowed (" + maxDiscountPct + "%)");
                }
            }
        }

        if (proposedAction == RecoveryAction.ESCALATE_TO_MERCHANT) {
            return PolicyDecision.requireApproval("Strategy Agent recommended escalation; merchant sign-off required");
        }

        return PolicyDecision.allow("All policy checks passed");
    }

    public PolicyDecision checkBatchCostBudget(BigDecimal cumulativeCostSoFar, BigDecimal thisActionCost) {
        BigDecimal maxTotalCost = policyRuleService.getDecimal("MAX_TOTAL_RECOVERY_COST", BigDecimal.valueOf(50000));
        BigDecimal projected = cumulativeCostSoFar.add(thisActionCost);
        if (projected.compareTo(maxTotalCost) > 0) {
            return PolicyDecision.block("Batch recovery cost budget (" + maxTotalCost + ") would be exceeded");
        }
        return PolicyDecision.allow("Within batch cost budget");
    }

    private boolean isCustomerFacingAction(RecoveryAction action) {
        return action == RecoveryAction.SEND_REMINDER
                || action == RecoveryAction.SEND_PAYMENT_LINK
                || action == RecoveryAction.OFFER_SMALL_INCENTIVE;
    }
}