package com.recoverai.service;

import com.recoverai.entity.enums.RecoveryAction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Single source of truth for "what does this action cost us to attempt".
 *
 * Lived as a private method inside StrategyAgent until now. The approval flow
 * needs the same numbers when it re-executes a previously-proposed action, and
 * two copies of a cost table is exactly the kind of thing that silently drifts.
 */
@Component
public class RecoveryCostCalculator {

    public static final BigDecimal COST_RETRY = BigDecimal.valueOf(5);
    public static final BigDecimal COST_PAYMENT_LINK = BigDecimal.valueOf(10);
    public static final BigDecimal COST_REMINDER = BigDecimal.valueOf(15);
    public static final BigDecimal COST_INCENTIVE_BASE = BigDecimal.valueOf(50);
    public static final BigDecimal COST_ESCALATION = BigDecimal.valueOf(100);

    public BigDecimal costOf(RecoveryAction action) {
        if (action == null) {
            return BigDecimal.ZERO;
        }
        return switch (action) {
            case RETRY_PAYMENT, WAIT_AND_RETRY -> COST_RETRY;
            case SEND_PAYMENT_LINK -> COST_PAYMENT_LINK;
            case SEND_REMINDER -> COST_REMINDER;
            case OFFER_SMALL_INCENTIVE -> COST_INCENTIVE_BASE;
            case ESCALATE_TO_MERCHANT -> COST_ESCALATION;
            case NO_ACTION -> BigDecimal.ZERO;
        };
    }
}