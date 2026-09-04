package com.recoverai.service;

import com.recoverai.audit.AuditService;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.Actor;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.policy.PolicyDecision;
import com.recoverai.razorpay.RazorpayClientService;
import com.recoverai.realtime.ActivityEvent;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The "act" step. One hard rule: this class never decides anything itself,
 * it only executes a PolicyDecision it is handed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryExecutor {

    private final RazorpayClientService razorpayClientService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AuditService auditService;
    private final ActivityStreamPublisher activityStreamPublisher;

    private final Random outcomeRandom = new Random(42);

    public RecoveryAttempt execute(RecoveryCase recoveryCase, RecoveryAction action,
                                   BigDecimal recoveryProbability, BigDecimal cost,
                                   PolicyDecision policyDecision) {

        int nextAttemptNumber = recoveryCase.getAttemptCount() + 1;

        if (!policyDecision.allowed()) {
            return recordBlocked(recoveryCase, action, nextAttemptNumber, policyDecision);
        }

        if (policyDecision.requiresMerchantApproval()) {
            return recordAwaitingApproval(recoveryCase, action, nextAttemptNumber, policyDecision);
        }

        return switch (action) {
            case NO_ACTION -> recordNoAction(recoveryCase, nextAttemptNumber);
            case RETRY_PAYMENT, WAIT_AND_RETRY -> executeRealRetry(recoveryCase, action, nextAttemptNumber, recoveryProbability, cost);
            case SEND_PAYMENT_LINK -> executeRealPaymentLink(recoveryCase, nextAttemptNumber, recoveryProbability, cost);
            case SEND_REMINDER, OFFER_SMALL_INCENTIVE -> executeSimulated(recoveryCase, action, nextAttemptNumber, recoveryProbability, cost);
            // Not redundant with the requiresMerchantApproval branch above: that branch is
            // the normal path. ApprovalService hands us a hand-built allow() decision, so
            // without this line an approved escalation would try to "execute an escalation",
            // which means nothing. This is the backstop.
            case ESCALATE_TO_MERCHANT -> recordAwaitingApproval(recoveryCase, action, nextAttemptNumber, policyDecision);
        };
    }

    private RecoveryAttempt executeRealRetry(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber,
                                             BigDecimal probability, BigDecimal cost) {
        try {
            publish(ActivityEvent.Stage.EXECUTED, recoveryCase,
                    "Creating real Razorpay order for " + action.name(),
                    Map.of("action", action.name(), "isRealApiAction", true));

            var order = razorpayClientService.createOrder(
                    recoveryCase.getAmountAtRisk(), "INR", recoveryCase.getPayment().getId().toString());

            boolean succeeded = simulateCustomerOutcome(probability);
            AttemptStatus status = succeeded ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            BigDecimal recovered = succeeded ? recoveryCase.getAmountAtRisk() : BigDecimal.ZERO;

            RecoveryAttempt attempt = save(recoveryCase, action, attemptNumber, status, true,
                    "Razorpay order " + order.orderId() + " (" + order.status() + "); demo outcome: " + status,
                    recovered, order.orderId());

            finalizeCase(recoveryCase, attempt, cost);
            auditService.log(recoveryCase, Actor.RAZORPAY, "ORDER_CREATED", null, order.status(),
                    "Real Razorpay Test Mode order created for retry", Map.of("orderId", order.orderId()));

            return attempt;
        } catch (Exception ex) {
            log.error("Razorpay order creation failed for case {}: {}", recoveryCase.getId(), ex.getMessage());
            RecoveryAttempt attempt = save(recoveryCase, action, attemptNumber, AttemptStatus.FAILED, true,
                    "Razorpay API call failed: " + ex.getMessage(), BigDecimal.ZERO);
            finalizeCase(recoveryCase, attempt, cost);
            return attempt;
        }
    }

    private RecoveryAttempt executeRealPaymentLink(RecoveryCase recoveryCase, int attemptNumber,
                                                   BigDecimal probability, BigDecimal cost) {
        try {
            publish(ActivityEvent.Stage.EXECUTED, recoveryCase,
                    "Creating real Razorpay payment link",
                    Map.of("action", RecoveryAction.SEND_PAYMENT_LINK.name(), "isRealApiAction", true));

            var customer = recoveryCase.getCustomer();
            var link = razorpayClientService.createPaymentLink(
                    recoveryCase.getAmountAtRisk(), "INR",
                    customer.getName(), customer.getEmail(), customer.getPhone(),
                    "RecoverAI recovery link for payment " + recoveryCase.getPayment().getId());

            boolean succeeded = simulateCustomerOutcome(probability);
            AttemptStatus status = succeeded ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            BigDecimal recovered = succeeded ? recoveryCase.getAmountAtRisk() : BigDecimal.ZERO;

            RecoveryAttempt attempt = save(recoveryCase, RecoveryAction.SEND_PAYMENT_LINK, attemptNumber, status, true,
                    "Razorpay payment link " + link.linkId() + "; demo outcome: " + status,
                    recovered, link.linkId());

            finalizeCase(recoveryCase, attempt, cost);
            auditService.log(recoveryCase, Actor.RAZORPAY, "PAYMENT_LINK_CREATED", null, link.status(),
                    "Real Razorpay Test Mode payment link created", Map.of("linkId", link.linkId(), "url", link.shortUrl()));

            return attempt;
        } catch (Exception ex) {
            log.error("Razorpay payment link creation failed for case {}: {}", recoveryCase.getId(), ex.getMessage());
            RecoveryAttempt attempt = save(recoveryCase, RecoveryAction.SEND_PAYMENT_LINK, attemptNumber, AttemptStatus.FAILED, true,
                    "Razorpay API call failed: " + ex.getMessage(), BigDecimal.ZERO);
            finalizeCase(recoveryCase, attempt, cost);
            return attempt;
        }
    }

    private RecoveryAttempt executeSimulated(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber,
                                             BigDecimal probability, BigDecimal cost) {
        publish(ActivityEvent.Stage.EXECUTED, recoveryCase,
                "Simulated " + action.name() + " (no real channel in hackathon scope)",
                Map.of("action", action.name(), "isRealApiAction", false));

        boolean succeeded = simulateCustomerOutcome(probability);
        AttemptStatus status = succeeded ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
        BigDecimal recovered = succeeded ? recoveryCase.getAmountAtRisk() : BigDecimal.ZERO;

        RecoveryAttempt attempt = save(recoveryCase, action, attemptNumber, status, false,
                "Simulated action (no real channel available in hackathon scope); demo outcome: " + status, recovered);

        finalizeCase(recoveryCase, attempt, cost);
        return attempt;
    }

    private RecoveryAttempt recordNoAction(RecoveryCase recoveryCase, int attemptNumber) {
        RecoveryAttempt attempt = save(recoveryCase, RecoveryAction.NO_ACTION, attemptNumber, AttemptStatus.EXECUTED, false,
                "No action taken: not economically worthwhile or insufficient confidence", BigDecimal.ZERO);

        recoveryCase.setStatus(CaseStatus.UNRECOVERABLE);
        recoveryCase.setResolvedAt(Instant.now());
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.SYSTEM, "CASE_RESOLVED", recoveryCase.getStatus().name(), CaseStatus.UNRECOVERABLE.name(),
                "No economically viable recovery action available", null);

        publish(ActivityEvent.Stage.RESULT, recoveryCase,
                "No action — not economically worthwhile",
                Map.of("action", RecoveryAction.NO_ACTION.name(), "caseStatus", CaseStatus.UNRECOVERABLE.name()));

        return attempt;
    }

    private RecoveryAttempt recordBlocked(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber, PolicyDecision decision) {
        RecoveryAttempt attempt = save(recoveryCase, action, attemptNumber, AttemptStatus.BLOCKED, false, decision.reason(), BigDecimal.ZERO);

        String previousStatus = recoveryCase.getStatus().name();
        recoveryCase.setStatus(CaseStatus.BLOCKED);
        recoveryCase.setResolvedAt(Instant.now());
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.POLICY_ENGINE, "ACTION_BLOCKED", previousStatus, CaseStatus.BLOCKED.name(),
                decision.reason(), Map.of("proposedAction", action.name()));

        publish(ActivityEvent.Stage.POLICY_BLOCKED, recoveryCase,
                "Policy blocked " + action.name() + " — " + decision.reason(),
                Map.of("proposedAction", action.name(), "reason", decision.reason()));

        return attempt;
    }

    private RecoveryAttempt recordAwaitingApproval(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber, PolicyDecision decision) {
        RecoveryAttempt attempt = save(recoveryCase, action, attemptNumber, AttemptStatus.PENDING, false, decision.reason(), BigDecimal.ZERO);

        String previousStatus = recoveryCase.getStatus().name();
        recoveryCase.setStatus(CaseStatus.AWAITING_APPROVAL);
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.POLICY_ENGINE, "AWAITING_MERCHANT_APPROVAL", previousStatus, CaseStatus.AWAITING_APPROVAL.name(),
                decision.reason(), Map.of("proposedAction", action.name()));

        publish(ActivityEvent.Stage.AWAITING_APPROVAL, recoveryCase,
                "Awaiting merchant approval for " + action.name() + " — " + decision.reason(),
                Map.of("proposedAction", action.name(), "reason", decision.reason()));

        return attempt;
    }

    private RecoveryAttempt save(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber, AttemptStatus status,
                                 boolean isReal, String result, BigDecimal amountRecovered) {
        return save(recoveryCase, action, attemptNumber, status, isReal, result, amountRecovered, null);
    }

    /**
     * externalRef is the Razorpay order/link id. Only the two real-API paths pass one;
     * everything else stores null, because there is no Razorpay object to point at.
     */
    private RecoveryAttempt save(RecoveryCase recoveryCase, RecoveryAction action, int attemptNumber, AttemptStatus status,
                                 boolean isReal, String result, BigDecimal amountRecovered, String externalRef) {
        RecoveryAttempt attempt = RecoveryAttempt.builder()
                .recoveryCase(recoveryCase)
                .attemptNumber(attemptNumber)
                .action(action)
                .status(status)
                .isRealApiAction(isReal)
                .executedAt(Instant.now())
                .result(result)
                .amountRecovered(amountRecovered)
                .externalRef(externalRef)
                .build();
        return recoveryAttemptRepository.save(attempt);
    }

    private void finalizeCase(RecoveryCase recoveryCase, RecoveryAttempt attempt, BigDecimal cost) {
        recoveryCase.setAttemptCount(recoveryCase.getAttemptCount() + 1);
        recoveryCase.setLastContactAt(Instant.now());
        recoveryCase.setRecoveryCost(recoveryCase.getRecoveryCost().add(cost));

        if (attempt.getStatus() == AttemptStatus.SUCCEEDED) {
            recoveryCase.setStatus(CaseStatus.RECOVERED);
            recoveryCase.setResolvedAt(Instant.now());
        } else {
            recoveryCase.setStatus(CaseStatus.IN_PROGRESS);
        }
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.SYSTEM, "ATTEMPT_RESULT", null, attempt.getStatus().name(),
                attempt.getResult(), Map.of("amountRecovered", attempt.getAmountRecovered()));

        // The "Result" line in the Screen 2 feed.
        Map<String, Object> data = new HashMap<>();
        data.put("attemptStatus", attempt.getStatus().name());
        data.put("action", attempt.getAction().name());
        data.put("amountRecovered", attempt.getAmountRecovered());
        data.put("recoveryCost", cost);
        data.put("caseStatus", recoveryCase.getStatus().name());
        data.put("isRealApiAction", attempt.getIsRealApiAction());

        publish(ActivityEvent.Stage.RESULT, recoveryCase,
                attempt.getStatus() == AttemptStatus.SUCCEEDED
                        ? "Recovered " + attempt.getAmountRecovered() + " via " + attempt.getAction().name()
                        : "Attempt " + attempt.getStatus().name().toLowerCase() + " for " + attempt.getAction().name(),
                data);
    }

    private boolean simulateCustomerOutcome(BigDecimal probability) {
        double p = probability == null ? 0.0 : probability.doubleValue();
        return outcomeRandom.nextDouble() < p;
    }

    /** Never let a dead browser connection break a recovery run. */
    private void publish(ActivityEvent.Stage stage, RecoveryCase recoveryCase,
                         String message, Map<String, Object> data) {
        try {
            activityStreamPublisher.publish(ActivityEvent.of(
                    stage,
                    recoveryCase.getId(),
                    recoveryCase.getPayment() == null ? null : String.valueOf(recoveryCase.getPayment().getId()),
                    message,
                    data == null ? Map.of() : new HashMap<>(data)));
        } catch (Exception ex) {
            log.debug("Activity publish failed (non-fatal): {}", ex.getMessage());
        }
    }
}
