package com.recoverai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.entity.enums.WebhookStatus;
import com.recoverai.razorpay.RazorpaySignatureVerifier;
import com.recoverai.realtime.ActivityEvent;
import com.recoverai.realtime.ActivityStreamPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates one inbound Razorpay webhook delivery: verify, claim, dispatch, record.
 *
 * Intentionally NOT @Transactional. Three different durability rules collide in this flow
 * and a single surrounding transaction cannot satisfy all of them:
 *
 *   - The event-id claim must commit before any work starts, or concurrent retries race.
 *   - The case update must be all-or-nothing.
 *   - The audit row must survive even when the case update rolls back.
 *
 * So the transactions live in the two collaborators ({@link WebhookEventRecorder} with
 * REQUIRES_NEW, {@link WebhookReconciler} with a plain one) and this class only sequences
 * them. It also means an exception from reconciliation unwinds cleanly to here, where it
 * can be recorded, instead of poisoning a transaction we are still trying to write into.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RazorpaySignatureVerifier signatureVerifier;
    private final WebhookEventRecorder recorder;
    private final WebhookReconciler reconciler;
    private final ObjectMapper objectMapper;
    private final ActivityStreamPublisher activityStreamPublisher;

    /** What the controller turns into an HTTP status. */
    public record WebhookResult(WebhookStatus status, String message, UUID caseId) {
    }

    public WebhookResult handle(String rawBody, String signatureHeader, String eventIdHeader) {
        String eventId = resolveEventId(eventIdHeader, rawBody);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (Exception ex) {
            // Recorded rather than dropped: a body we cannot parse is exactly the kind of
            // integration problem that is invisible if you only log it and return 200.
            safeRecordRejected(eventId, "unparseable", WebhookStatus.FAILED,
                    "Body was not valid JSON: " + ex.getMessage());
            return new WebhookResult(WebhookStatus.FAILED, "Malformed JSON body", null);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            safeRecordRejected(eventId, "empty", WebhookStatus.FAILED, "Empty request body");
            return new WebhookResult(WebhookStatus.FAILED, "Empty request body", null);
        }

        String eventType = text(root, "event");

        // Signature is checked against the untouched request string. Anything derived from
        // `root` above is used only for labelling the rejection row — never acted upon.
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            String why = signatureVerifier.isConfigured()
                    ? "X-Razorpay-Signature did not match the HMAC-SHA256 of the raw body"
                    : "razorpay.webhook-secret is not configured, so no delivery can be trusted";
            safeRecordRejected(eventId, eventType, WebhookStatus.INVALID_SIGNATURE, why);
            publish(ActivityEvent.Stage.WEBHOOK_REJECTED, null,
                    "Webhook rejected — invalid signature",
                    Map.of("eventType", nullToUnknown(eventType), "reason", why));
            log.warn("Rejected webhook {} ({}): {}", eventId, nullToUnknown(eventType), why);
            return new WebhookResult(WebhookStatus.INVALID_SIGNATURE, why, null);
        }

        Map<String, Object> payload = toMap(root);

        UUID webhookEventId;
        try {
            webhookEventId = recorder.claim(eventId, eventType, true, payload).getId();
        } catch (DataIntegrityViolationException ex) {
            // The UNIQUE constraint on razorpay_event_id fired. This is the idempotency guard
            // working, not a fault: Razorpay retries until it gets a 2xx, so replays are the
            // common case. Caught HERE rather than inside the recorder because the recorder's
            // transaction is already marked rollback-only by the time the exception is thrown —
            // swallowing it in there would just move the failure to commit time.
            log.info("Webhook {} was already claimed, treating delivery as a replay", eventId);
            return new WebhookResult(WebhookStatus.DUPLICATE,
                    "Event " + eventId + " was already processed; ignored as a replay", null);
        }

        publish(ActivityEvent.Stage.WEBHOOK_RECEIVED, null,
                "Webhook received — " + nullToUnknown(eventType),
                Map.of("eventType", nullToUnknown(eventType), "eventId", eventId));

        WebhookReconciler.Outcome outcome;
        try {
            outcome = dispatch(root, eventType);
        } catch (Exception ex) {
            log.error("Webhook {} failed during reconciliation: {}", eventId, ex.getMessage(), ex);
            recorder.finish(webhookEventId, WebhookStatus.FAILED, null, null,
                    "Reconciliation threw: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            publish(ActivityEvent.Stage.WEBHOOK_REJECTED, null,
                    "Webhook processing failed — " + nullToUnknown(eventType),
                    Map.of("eventType", nullToUnknown(eventType), "error", String.valueOf(ex.getMessage())));
            return new WebhookResult(WebhookStatus.FAILED, "Reconciliation failed: " + ex.getMessage(), null);
        }

        recorder.finish(webhookEventId, outcome.status(), outcome.caseId(), outcome.externalRef(), outcome.note());

        if (outcome.status() == WebhookStatus.PROCESSED) {
            Map<String, Object> data = new HashMap<>();
            data.put("eventType", nullToUnknown(eventType));
            data.put("externalRef", outcome.externalRef());
            data.put("amountRecovered", outcome.amountRecovered());
            data.put("caseId", outcome.caseId() == null ? null : outcome.caseId().toString());
            publish(ActivityEvent.Stage.WEBHOOK_RECONCILED, outcome.caseId(),
                    "Razorpay confirmed — " + outcome.note(), data);
        } else {
            publish(ActivityEvent.Stage.WEBHOOK_REJECTED, outcome.caseId(),
                    "Webhook " + outcome.status().name().toLowerCase() + " — " + outcome.note(),
                    Map.of("eventType", nullToUnknown(eventType), "status", outcome.status().name()));
        }

        return new WebhookResult(outcome.status(), outcome.note(), outcome.caseId());
    }

    private WebhookReconciler.Outcome dispatch(JsonNode root, String eventType) {
        List<String> refs = candidateRefs(root);
        String razorpayPaymentId = text(root.at("/payload/payment/entity"), "id");

        return switch (eventType == null ? "" : eventType) {
            case "payment.captured", "order.paid", "payment_link.paid" ->
                    reconciler.reconcileSuccess(refs, razorpayPaymentId, confirmedAmount(root), eventType);

            case "payment.failed" ->
                    reconciler.reconcileFailure(refs, razorpayPaymentId,
                            text(root.at("/payload/payment/entity"), "error_description"), eventType);

            // payment.authorized is deliberately not handled: orders are created with
            // payment_capture=1, so an authorization is always followed by a capture event.
            // Acting on the earlier one would count the revenue before it is actually settled.
            default -> WebhookReconciler.Outcome.ignored(
                    "Event type not handled by RecoverAI: " + nullToUnknown(eventType));
        };
    }

    /**
     * Razorpay puts the id we stored in a different place depending on event type, so instead
     * of branching we collect every plausible ref and let the reconciler try them in order.
     * Payment-link id comes first because a link's internal order id is one we never saw.
     */
    private List<String> candidateRefs(JsonNode root) {
        List<String> refs = new ArrayList<>();
        refs.add(text(root.at("/payload/payment_link/entity"), "id"));
        refs.add(text(root.at("/payload/payment/entity"), "order_id"));
        refs.add(text(root.at("/payload/order/entity"), "id"));
        return refs;
    }

    /** Amounts arrive as integer paise. Fall back through the entities that might carry one. */
    private BigDecimal confirmedAmount(JsonNode root) {
        Long paise = firstNonNull(
                longValue(root.at("/payload/payment/entity"), "amount"),
                longValue(root.at("/payload/order/entity"), "amount_paid"),
                longValue(root.at("/payload/payment_link/entity"), "amount_paid"),
                longValue(root.at("/payload/payment_link/entity"), "amount"),
                longValue(root.at("/payload/order/entity"), "amount"));
        return WebhookReconciler.paiseToRupees(paise);
    }

    /**
     * Falls back to a hash of the body when the x-razorpay-event-id header is absent, so the
     * idempotency guard still works for hand-crafted curl tests. Prefixed to make it obvious
     * in the table which ids were synthesised rather than sent by Razorpay.
     */
    private String resolveEventId(String eventIdHeader, String rawBody) {
        if (eventIdHeader != null && !eventIdHeader.isBlank()) {
            String trimmed = eventIdHeader.trim();
            return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return "derived-" + HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (Exception ex) {
            return "derived-" + UUID.randomUUID();
        }
    }

    private Map<String, Object> toMap(JsonNode root) {
        try {
            return objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.debug("Could not convert webhook body to a map, storing null payload: {}", ex.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String asText = value.asText();
        return asText.isBlank() ? null : asText;
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /** A dead browser tab must never turn a real webhook into a 500 that makes Razorpay retry. */
    private void publish(ActivityEvent.Stage stage, UUID caseId, String message, Map<String, Object> data) {
        try {
            activityStreamPublisher.publish(ActivityEvent.of(stage, caseId, null, message,
                    data == null ? Map.of() : new HashMap<>(data)));
        } catch (Exception ex) {
            log.debug("Activity publish failed (non-fatal): {}", ex.getMessage());
        }
    }

    /**
     * Recording a rejection is best-effort. A forged request can deliberately reuse an event
     * id that is already in the table, and the resulting constraint violation must not turn a
     * clean 400 into a 500 — that would hide the fact that we rejected it, and hand an
     * attacker a way to tell which event ids exist.
     */
    private void safeRecordRejected(String eventId, String eventType, WebhookStatus status, String note) {
        try {
            recorder.recordRejected(eventId, eventType, status, note);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Rejected webhook {} reused an existing event id; not recording a second row", eventId);
        } catch (Exception ex) {
            log.error("Could not record rejected webhook {}: {}", eventId, ex.getMessage());
        }
    }
}
