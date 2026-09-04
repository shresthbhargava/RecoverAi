package com.recoverai.entity.enums;

/**
 * The FIXED, bounded set of actions the Strategy Agent (LLM) may propose.
 * This enum is the hard boundary of the AI's action space — the model is
 * instructed to select only from these values, and any output outside this
 * set is rejected by schema validation before it ever reaches the Policy Engine.
 */
public enum RecoveryAction {
    RETRY_PAYMENT,
    WAIT_AND_RETRY,
    SEND_PAYMENT_LINK,
    SEND_REMINDER,
    OFFER_SMALL_INCENTIVE,
    ESCALATE_TO_MERCHANT,
    NO_ACTION
}
