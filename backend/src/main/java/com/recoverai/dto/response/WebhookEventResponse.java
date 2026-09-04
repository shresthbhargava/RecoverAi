package com.recoverai.dto.response;

import com.recoverai.entity.WebhookEvent;
import com.recoverai.entity.enums.WebhookStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for GET /api/webhooks/recent.
 *
 * Deliberately omits the stored payload. It is kept in the table for diagnosis, but a
 * webhook body contains customer email and contact number, and there is no reason to hand
 * that to a dashboard that only needs to show that verification and idempotency worked.
 */
public record WebhookEventResponse(
        UUID id,
        String razorpayEventId,
        String eventType,
        boolean signatureValid,
        WebhookStatus status,
        UUID relatedCaseId,
        String externalRef,
        String note,
        Instant receivedAt,
        Instant processedAt
) {

    public static WebhookEventResponse from(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getId(),
                event.getRazorpayEventId(),
                event.getEventType(),
                Boolean.TRUE.equals(event.getSignatureValid()),
                event.getStatus(),
                // relatedCase is a LAZY proxy; getId() reads the foreign key already in hand
                // and does not trigger a select, so this is safe outside a session.
                event.getRelatedCase() == null ? null : event.getRelatedCase().getId(),
                event.getExternalRef(),
                event.getNote(),
                event.getReceivedAt(),
                event.getProcessedAt()
        );
    }
}
