package com.recoverai.service;

import com.recoverai.entity.WebhookEvent;
import com.recoverai.entity.enums.WebhookStatus;
import com.recoverai.repository.RecoveryCaseRepository;
import com.recoverai.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the webhook_event table, and nothing else.
 *
 * This is a separate bean rather than a few methods on {@link WebhookService} for one
 * specific reason: Spring's @Transactional is implemented with a proxy, so calling a
 * @Transactional method on {@code this} from inside the same class silently bypasses it and
 * runs in the caller's transaction. Every method here needs REQUIRES_NEW to commit
 * independently of whatever the caller is doing, so they cannot live on the caller.
 *
 * Why independent commits matter: if reconciliation throws, its transaction rolls back.
 * Anything written in that same transaction — including the row recording that the webhook
 * arrived and failed — would roll back with it, leaving no trace of the failure.
 *
 * Note what this class does NOT do: it does not catch DataIntegrityViolationException.
 * Catching a constraint violation inside the transaction that caused it does not work —
 * Hibernate marks the transaction rollback-only at flush time, so returning normally makes
 * the subsequent commit fail with UnexpectedRollbackException instead. The violation has to
 * escape this transaction and be handled by the non-transactional caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventRecorder {

    private final WebhookEventRepository webhookEventRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    /**
     * Claims an event id by inserting a RECEIVED row, and commits immediately.
     *
     * The claim is an INSERT rather than an exists() check followed by a write. That
     * distinction is the whole idempotency guarantee: Razorpay retries in parallel with the
     * original delivery, so a read-then-write leaves a window where two threads both see
     * "not present" and both credit the same payment. Letting the UNIQUE constraint arbitrate
     * closes that window in the database, where it cannot race.
     *
     * saveAndFlush, not save: the INSERT has to reach Postgres inside this call so the
     * violation surfaces here rather than at commit, after the caller has moved on.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if the id was already
     *         claimed, i.e. this delivery is a replay. Expected, not exceptional.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEvent claim(String razorpayEventId, String eventType,
                             boolean signatureValid, Map<String, Object> payload) {
        return webhookEventRepository.saveAndFlush(WebhookEvent.builder()
                .razorpayEventId(razorpayEventId)
                .eventType(eventType == null ? "unknown" : eventType)
                .signatureValid(signatureValid)
                .status(WebhookStatus.RECEIVED)
                .payload(payload)
                .receivedAt(Instant.now())
                .build());
    }

    /**
     * Writes the outcome onto an already-claimed row. Re-reads by id inside this new
     * transaction because the instance the caller holds was loaded in a different one and is
     * detached by the time reconciliation finishes.
     *
     * Takes the case as a UUID rather than an entity for the same reason — the RecoveryCase
     * the reconciler touched belongs to a transaction that has already committed and closed.
     * getReferenceById gives a proxy attached to THIS transaction, which is all Hibernate
     * needs to write the foreign key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID webhookEventId, WebhookStatus status,
                       UUID relatedCaseId, String externalRef, String note) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setStatus(status);
            event.setRelatedCase(relatedCaseId == null ? null : recoveryCaseRepository.getReferenceById(relatedCaseId));
            event.setExternalRef(externalRef);
            event.setNote(truncate(note));
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
        });
    }

    /**
     * Records a delivery refused before an id was claimed — a failed HMAC, or a body that
     * would not parse.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if a forged request
     *         reused an existing event id. The caller decides whether that matters.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(String razorpayEventId, String eventType, WebhookStatus status, String note) {
        webhookEventRepository.saveAndFlush(WebhookEvent.builder()
                .razorpayEventId(razorpayEventId)
                .eventType(eventType == null ? "unknown" : eventType)
                .signatureValid(false)
                .status(status)
                .note(truncate(note))
                .receivedAt(Instant.now())
                .processedAt(Instant.now())
                .build());
    }

    /** note is TEXT in Postgres, but keep rows readable and bound the damage from a hostile body. */
    private String truncate(String note) {
        if (note == null) return null;
        return note.length() <= 1000 ? note : note.substring(0, 1000) + "…";
    }
}
