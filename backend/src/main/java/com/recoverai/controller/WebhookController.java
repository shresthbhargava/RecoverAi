package com.recoverai.controller;

import com.recoverai.dto.response.WebhookAckResponse;
import com.recoverai.dto.response.WebhookEventResponse;
import com.recoverai.entity.enums.WebhookStatus;
import com.recoverai.repository.WebhookEventRepository;
import com.recoverai.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookEventRepository webhookEventRepository;

    /**
     * The Razorpay callback target.
     *
     * The body is taken as a String and never as a mapped DTO. This is not a style choice:
     * Razorpay signs the exact bytes it sent, and letting Jackson deserialise then
     * re-serialise reorders object keys and normalises whitespace, so the recomputed HMAC
     * would not match and every single delivery would be rejected as forged.
     *
     * Status codes matter here, because they control Razorpay's retry behaviour:
     *   400 — bad signature. We do not want this delivery back.
     *   200 — everything else, including a failed reconciliation.
     *
     * Returning 200 on failure is a deliberate trade-off, not an oversight. The event id is
     * already claimed by the time reconciliation runs, so a Razorpay retry would hit the
     * UNIQUE constraint and be classified as a replay rather than actually retried. Given
     * automatic retry cannot help, the honest behaviour is to acknowledge the delivery and
     * leave a FAILED row visible at GET /api/webhooks/recent for manual replay, instead of
     * making Razorpay hammer an endpoint that will keep short-circuiting.
     */
    @PostMapping("/razorpay")
    public ResponseEntity<WebhookAckResponse> razorpay(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {

        WebhookService.WebhookResult result = webhookService.handle(rawBody, signature, eventId);

        HttpStatus httpStatus = result.status() == WebhookStatus.INVALID_SIGNATURE
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.OK;

        return ResponseEntity.status(httpStatus)
                .body(new WebhookAckResponse(result.status(), result.message(), result.caseId()));
    }

    /**
     * Delivery log, newest first. Includes the rejected and duplicate rows — those are the
     * ones worth showing, since they are the evidence that signature verification and the
     * idempotency guard are doing something rather than just being described in a README.
     */
    @GetMapping("/recent")
    public List<WebhookEventResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return webhookEventRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, capped)).stream()
                .map(WebhookEventResponse::from)
                .toList();
    }

    /** Counts straight out of the table — no constants, so the numbers cannot drift from reality. */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (WebhookStatus status : WebhookStatus.values()) {
            stats.put(status.name(), webhookEventRepository.countByStatus(status));
        }
        stats.put("TOTAL", webhookEventRepository.count());
        return stats;
    }
}
