package com.recoverai.service;

import com.recoverai.audit.AuditService;
import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.Actor;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.PaymentStatus;
import com.recoverai.entity.enums.WebhookStatus;
import com.recoverai.repository.PaymentRepository;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies confirmed Razorpay facts to a recovery case, atomically.
 *
 * Separate bean from {@link WebhookService} on purpose. Everything in here has to succeed
 * or fail as one unit — an attempt marked SUCCEEDED while its case still reads IN_PROGRESS
 * would make the analytics aggregates contradict each other, and those aggregates are what
 * the dashboard reports as recovered revenue. Spring's @Transactional only applies through
 * the bean proxy, so a private method on the orchestrator would silently get no transaction
 * at all. Putting it in its own bean is what makes the annotation real.
 *
 * One rule this class holds to, matching {@code RecoveryExecutor}: a webhook records facts,
 * it never makes decisions. So a confirmed capture does resolve a case as RECOVERED, but a
 * reported failure does NOT mark a case UNRECOVERABLE — only the agents and the Policy
 * Engine get to conclude that, on the next run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookReconciler {

    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    /** recovery_attempt.result is VARCHAR(255); anything longer must be cut before the insert. */
    private static final int RESULT_MAX = 255;

    /**
     * @param status       what to record on the webhook_event row
     * @param caseId       null when nothing of ours matched
     * @param externalRef  the ref that actually matched, so the row shows how correlation happened
     */
    public record Outcome(WebhookStatus status, UUID caseId, String externalRef,
                          String note, BigDecimal amountRecovered) {

        static Outcome ignored(String note) {
            return new Outcome(WebhookStatus.IGNORED, null, null, note, BigDecimal.ZERO);
        }
    }

    /**
     * A confirmed capture. Correlates on any of the candidate refs, then makes the case agree
     * with what actually happened at Razorpay.
     *
     * @param candidateRefs ordered ids to try — order_id, payment link id, order id. Razorpay
     *                      puts the useful one in a different place per event type, so rather
     *                      than trusting one path we try each and use whichever matches.
     */
    @Transactional
    public Outcome reconcileSuccess(List<String> candidateRefs, String razorpayPaymentId,
                                    BigDecimal confirmedAmount, String eventType) {

        Optional<Match> found = locate(candidateRefs);
        if (found.isEmpty()) {
            return Outcome.ignored("No recovery attempt matches " + describe(candidateRefs)
                    + ". Validly signed but not ours — most likely another integration on the same Test Mode account.");
        }

        RecoveryAttempt attempt = found.get().attempt();
        String matchedRef = found.get().ref();
        RecoveryCase recoveryCase = attempt.getRecoveryCase();

        // The idempotency backstop behind the UNIQUE event id. Razorpay can emit several
        // distinct events for one payment (order.paid AND payment.captured), each with its own
        // event id, so the unique constraint alone would let the amount be added twice.
        if (attempt.getStatus() == AttemptStatus.SUCCEEDED) {
            return new Outcome(WebhookStatus.DUPLICATE, recoveryCase.getId(), matchedRef,
                    "Attempt " + attempt.getId() + " was already SUCCEEDED; revenue not counted again.",
                    BigDecimal.ZERO);
        }

        boolean overrodeSimulatedFailure = attempt.getStatus() == AttemptStatus.FAILED;
        String previousCaseStatus = recoveryCase.getStatus().name();

        BigDecimal recovered = confirmedAmount != null && confirmedAmount.signum() > 0
                ? confirmedAmount
                : recoveryCase.getAmountAtRisk();

        attempt.setStatus(AttemptStatus.SUCCEEDED);
        attempt.setAmountRecovered(recovered);
        attempt.setResult(clamp("Confirmed by Razorpay " + eventType
                + (razorpayPaymentId == null ? "" : " (payment " + razorpayPaymentId + ")")));
        recoveryAttemptRepository.save(attempt);

        Payment payment = recoveryCase.getPayment();
        if (payment != null) {
            if (razorpayPaymentId != null && !razorpayPaymentId.isBlank()) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
            }
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
        }

        recoveryCase.setStatus(CaseStatus.RECOVERED);
        recoveryCase.setResolvedAt(Instant.now());
        // Deliberately NOT touched: attemptCount and recoveryCost. RecoveryExecutor.finalizeCase
        // already incremented the count and charged the cost when it made the API call. This is
        // the same attempt being confirmed, not a new one.
        recoveryCaseRepository.save(recoveryCase);

        if (overrodeSimulatedFailure && recoveryCase.getCustomer() != null) {
            customerService.reclassifyFailureAsSuccess(recoveryCase.getCustomer());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", eventType);
        metadata.put("matchedExternalRef", matchedRef);
        metadata.put("razorpayPaymentId", razorpayPaymentId);
        metadata.put("amountRecovered", recovered);
        metadata.put("overrodeSimulatedFailure", overrodeSimulatedFailure);

        auditService.log(recoveryCase, Actor.RAZORPAY, "WEBHOOK_PAYMENT_CONFIRMED",
                previousCaseStatus, CaseStatus.RECOVERED.name(),
                overrodeSimulatedFailure
                        ? "Razorpay confirmed real capture; this overrides the simulated FAILED outcome recorded during the batch run"
                        : "Razorpay confirmed real capture",
                metadata);

        log.info("Webhook reconciled case {} as RECOVERED via {} (ref {})",
                recoveryCase.getId(), eventType, matchedRef);

        return new Outcome(WebhookStatus.PROCESSED, recoveryCase.getId(), matchedRef,
                "Case marked RECOVERED, " + recovered + " confirmed"
                        + (overrodeSimulatedFailure ? " (overrode simulated failure)" : ""),
                recovered);
    }

    /**
     * A reported failure. Records it on the attempt and stops there.
     *
     * Note what this does not do: it does not set the case UNRECOVERABLE, and it does not
     * pick a next action. Deciding a case is finished is the Policy Engine's and the agents'
     * job, and letting an inbound HTTP request short-circuit that would put decision-making
     * back in a place the architecture deliberately keeps it out of.
     */
    @Transactional
    public Outcome reconcileFailure(List<String> candidateRefs, String razorpayPaymentId,
                                    String errorDescription, String eventType) {

        Optional<Match> found = locate(candidateRefs);
        if (found.isEmpty()) {
            return Outcome.ignored("No recovery attempt matches " + describe(candidateRefs) + " (failure event).");
        }

        RecoveryAttempt attempt = found.get().attempt();
        String matchedRef = found.get().ref();
        RecoveryCase recoveryCase = attempt.getRecoveryCase();

        // Never walk back a confirmed success on the strength of a later failure event —
        // in practice that ordering means an unrelated retry on the same order.
        if (attempt.getStatus() == AttemptStatus.SUCCEEDED) {
            return new Outcome(WebhookStatus.IGNORED, recoveryCase.getId(), matchedRef,
                    "Attempt is already SUCCEEDED; refusing to downgrade it from a failure event.",
                    BigDecimal.ZERO);
        }

        attempt.setStatus(AttemptStatus.FAILED);
        attempt.setResult(clamp("Razorpay reported failure"
                + (errorDescription == null ? "" : ": " + errorDescription)));
        recoveryAttemptRepository.save(attempt);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", eventType);
        metadata.put("matchedExternalRef", matchedRef);
        metadata.put("razorpayPaymentId", razorpayPaymentId);
        metadata.put("errorDescription", errorDescription);

        auditService.log(recoveryCase, Actor.RAZORPAY, "WEBHOOK_PAYMENT_FAILED",
                attempt.getStatus().name(), AttemptStatus.FAILED.name(),
                "Razorpay reported the recovery payment failed; case status left unchanged for the agents to reassess",
                metadata);

        return new Outcome(WebhookStatus.PROCESSED, recoveryCase.getId(), matchedRef,
                "Attempt marked FAILED from Razorpay callback; case left for reassessment.", BigDecimal.ZERO);
    }

    /** Tries each candidate ref in order and returns the first attempt that matches. */
    private Optional<Match> locate(List<String> candidateRefs) {
        for (String ref : dedupe(candidateRefs)) {
            Optional<RecoveryAttempt> attempt =
                    recoveryAttemptRepository.findFirstByExternalRefOrderByExecutedAtDesc(ref);
            if (attempt.isPresent()) {
                return Optional.of(new Match(attempt.get(), ref));
            }
        }
        return Optional.empty();
    }

    private Set<String> dedupe(List<String> refs) {
        Set<String> out = new LinkedHashSet<>();
        if (refs != null) {
            for (String r : refs) {
                if (r != null && !r.isBlank()) out.add(r.trim());
            }
        }
        return out;
    }

    private String describe(List<String> refs) {
        Set<String> clean = dedupe(refs);
        return clean.isEmpty() ? "any known reference (payload carried none)" : String.join(" / ", clean);
    }

    private String clamp(String value) {
        if (value == null) return null;
        return value.length() <= RESULT_MAX ? value : value.substring(0, RESULT_MAX - 1) + "…";
    }

    /** Converts Razorpay's integer paise into the rupee scale the rest of the system uses. */
    public static BigDecimal paiseToRupees(Long paise) {
        if (paise == null) return null;
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private record Match(RecoveryAttempt attempt, String ref) {
    }
}
