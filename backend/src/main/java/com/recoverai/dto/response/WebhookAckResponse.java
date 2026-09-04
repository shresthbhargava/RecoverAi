package com.recoverai.dto.response;

import com.recoverai.entity.enums.WebhookStatus;

import java.util.UUID;

/**
 * What Razorpay gets back. Razorpay does not read the body — it only looks at the status
 * code — but returning the verdict makes the endpoint testable with plain curl, which is
 * how the demo drives it without waiting on a real card payment.
 */
public record WebhookAckResponse(
        WebhookStatus status,
        String message,
        UUID caseId
) {
}
