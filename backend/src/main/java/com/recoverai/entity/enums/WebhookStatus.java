package com.recoverai.entity.enums;

/**
 * Outcome of processing one inbound Razorpay webhook delivery. Every delivery gets a
 * row regardless of outcome — the rejected ones are the interesting evidence that
 * signature verification and idempotency are actually doing something.
 */
public enum WebhookStatus {

    /**
     * Row inserted, processing not finished yet. This state exists to resolve a conflict:
     * idempotency needs the UNIQUE razorpay_event_id claimed BEFORE any work happens, but
     * the audit trail needs a row to survive even if that work then throws. Inserting as
     * RECEIVED and updating afterwards satisfies both. A row still sitting at RECEIVED means
     * the JVM died mid-delivery — that is a real signal, not noise.
     */
    RECEIVED,

    /** Signature valid, event matched one of our attempts, case state updated. */
    PROCESSED,

    /**
     * This delivery was a replay. Either the razorpay_event_id was already claimed, or the
     * matched attempt was already SUCCEEDED. Nothing was written, so revenue is never
     * double-counted no matter how many times Razorpay retries.
     */
    DUPLICATE,

    /** Signature valid, but nothing in our system corresponds to it, or we don't handle this event type. */
    IGNORED,

    /** HMAC did not match. Nothing was written to any case. */
    INVALID_SIGNATURE,

    /** Signature valid but processing threw. Kept so failures are visible rather than silent. */
    FAILED
}
