package com.recoverai.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line in the Screen 2 live feed. Deliberately a flat, frontend-shaped record:
 * the UI picks its icon and colour off `stage`, prints `message`, and can drill into
 * `data` for the numbers without any string parsing.
 */
public record ActivityEvent(
        String id,
        Instant at,
        Stage stage,
        UUID caseId,
        String paymentRef,
        String message,
        Map<String, Object> data
) {

    public enum Stage {
        BATCH_STARTED,
        DETECTED,
        DIAGNOSED,
        STRATEGY_SELECTED,
        POLICY_BLOCKED,
        AWAITING_APPROVAL,
        APPROVAL_RESOLVED,
        EXECUTED,
        RESULT,
        BATCH_COMPLETED,

        /* --- Phase 4: inbound Razorpay callbacks. Unlike every stage above, these are not
           produced by our own run loop — they arrive from outside, possibly minutes later,
           long after the batch that created the order has finished. The UI should render
           them as a distinct track rather than appending to the batch timeline. --- */

        /** A signed delivery arrived and was accepted for processing. */
        WEBHOOK_RECEIVED,

        /** Razorpay confirmed real money moved, and the case has been updated to match. */
        WEBHOOK_RECONCILED,

        /** Refused: bad HMAC, unhandled event type, replay, or nothing of ours to match. */
        WEBHOOK_REJECTED
    }

    public static ActivityEvent of(Stage stage, UUID caseId, String paymentRef,
                                   String message, Map<String, Object> data) {
        return new ActivityEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                stage,
                caseId,
                paymentRef,
                message,
                data == null ? Map.of() : data
        );
    }
}