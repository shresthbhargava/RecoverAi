package com.recoverai.service;

import com.recoverai.audit.AuditService;
import com.recoverai.dto.request.ApprovalDecisionRequest;
import com.recoverai.dto.response.ApprovalResultResponse;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.Actor;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.exception.PolicyViolationException;
import com.recoverai.exception.ResourceNotFoundException;
import com.recoverai.policy.PolicyDecision;
import com.recoverai.policy.PolicyEngine;
import com.recoverai.realtime.ActivityEvent;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The human end of the escalation gate.
 *
 * Design rule, and the reason this class exists at all: merchant approval overrides
 * the APPROVAL GATE, never the HARD BLOCKS. A merchant may say "yes, spend the money"
 * on an escalated case; a merchant may not say "yes, contact this customer" after the
 * customer opted out, or "yes, try a 5th time" past MAX_RECOVERY_ATTEMPTS. So the
 * Policy Engine is re-run at approval time and only its requiresMerchantApproval
 * verdict is converted to an allow — a block stays a block.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PolicyEngine policyEngine;
    private final RecoveryExecutor recoveryExecutor;
    private final RecoveryCostCalculator recoveryCostCalculator;
    private final AuditService auditService;
    private final ActivityStreamPublisher activityStreamPublisher;

    @Transactional
    public ApprovalResultResponse approve(UUID caseId, ApprovalDecisionRequest request) {
        RecoveryCase recoveryCase = loadAwaitingApproval(caseId);
        RecoveryAttempt pending = loadPendingAttempt(recoveryCase);

        RecoveryAction action = resolveAction(recoveryCase, request.approvedAction());
        BigDecimal cost = recoveryCostCalculator.costOf(action);
        BigDecimal probability = recoveryCase.getRecoveryProbability() == null
                ? BigDecimal.ZERO
                : recoveryCase.getRecoveryProbability();

        // Re-run policy BEFORE touching attemptCount, so the approval round-trip
        // itself can't be what pushes the case over MAX_RECOVERY_ATTEMPTS.
        PolicyDecision recheck = policyEngine.evaluate(
                recoveryCase, recoveryCase.getCustomer(), action, cost);

        if (!recheck.allowed() && !recheck.requiresMerchantApproval()) {
            auditService.log(recoveryCase, Actor.POLICY_ENGINE, "APPROVAL_OVERRIDDEN_BY_POLICY",
                    recoveryCase.getStatus().name(), recoveryCase.getStatus().name(),
                    recheck.reason(), Map.of("requestedAction", action.name(),
                            "approvedBy", request.approvedBy()));
            throw new PolicyViolationException(
                    "Merchant approval cannot override this policy block: " + recheck.reason());
        }

        PolicyDecision effective = PolicyDecision.allow(
                "Approved by " + request.approvedBy() + " (gate: " + recheck.reason() + ")");

        // Close out the approval-request row so it can't collide with the executed
        // attempt's number, and so the trail reads: requested -> approved -> executed.
        pending.setStatus(AttemptStatus.EXECUTED);
        pending.setResult("Approved by " + request.approvedBy()
                + (request.note() == null || request.note().isBlank() ? "" : " — " + request.note())
                + "; executing " + action.name());
        recoveryAttemptRepository.save(pending);

        recoveryCase.setAttemptCount(Math.max(recoveryCase.getAttemptCount(), pending.getAttemptNumber()));
        recoveryCase.setStatus(CaseStatus.IN_PROGRESS);
        recoveryCase.setSelectedStrategy(action);
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.MERCHANT, "APPROVAL_GRANTED",
                CaseStatus.AWAITING_APPROVAL.name(), CaseStatus.IN_PROGRESS.name(),
                effective.reason(), Map.of("approvedAction", action.name(),
                        "approvedBy", request.approvedBy()));

        publish(ActivityEvent.Stage.APPROVAL_RESOLVED, recoveryCase,
                "Merchant approved " + action.name() + " (by " + request.approvedBy() + ")",
                Map.of("approvedBy", request.approvedBy(), "action", action.name()));

        RecoveryAttempt executed = recoveryExecutor.execute(
                recoveryCase, action, probability, cost, effective);

        return new ApprovalResultResponse(
                recoveryCase.getId(),
                "APPROVED",
                action,
                recoveryCase.getStatus().name(),
                executed.getStatus().name(),
                executed.getAmountRecovered(),
                "Approved and executed " + action.name()
        );
    }

    @Transactional
    public ApprovalResultResponse reject(UUID caseId, ApprovalDecisionRequest request) {
        RecoveryCase recoveryCase = loadAwaitingApproval(caseId);
        RecoveryAttempt pending = loadPendingAttempt(recoveryCase);

        String reason = "Rejected by " + request.approvedBy()
                + (request.note() == null || request.note().isBlank() ? "" : " — " + request.note());

        // BLOCKED, not FAILED: nothing was ever attempted against the customer.
        // This also makes merchant rejections show up in the Screen 4 "Actions Blocked"
        // count alongside policy blocks, which is exactly what they are.
        pending.setStatus(AttemptStatus.BLOCKED);
        pending.setResult(reason);
        recoveryAttemptRepository.save(pending);

        recoveryCase.setStatus(CaseStatus.BLOCKED);
        recoveryCase.setResolvedAt(Instant.now());
        recoveryCaseRepository.save(recoveryCase);

        auditService.log(recoveryCase, Actor.MERCHANT, "APPROVAL_REJECTED",
                CaseStatus.AWAITING_APPROVAL.name(), CaseStatus.BLOCKED.name(),
                reason, Map.of("rejectedBy", request.approvedBy()));

        publish(ActivityEvent.Stage.APPROVAL_RESOLVED, recoveryCase, reason,
                Map.of("rejectedBy", request.approvedBy()));

        return new ApprovalResultResponse(
                recoveryCase.getId(),
                "REJECTED",
                null,
                recoveryCase.getStatus().name(),
                pending.getStatus().name(),
                BigDecimal.ZERO,
                reason
        );
    }

    private RecoveryCase loadAwaitingApproval(UUID caseId) {
        RecoveryCase recoveryCase = recoveryCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Recovery case not found: " + caseId));

        if (recoveryCase.getStatus() != CaseStatus.AWAITING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Case " + caseId + " is not awaiting approval (current status: "
                            + recoveryCase.getStatus() + ")");
        }
        return recoveryCase;
    }

    private RecoveryAttempt loadPendingAttempt(RecoveryCase recoveryCase) {
        return recoveryAttemptRepository
                .findFirstByRecoveryCaseIdAndStatusOrderByAttemptNumberDesc(
                        recoveryCase.getId(), AttemptStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException(
                        "Case " + recoveryCase.getId()
                                + " is AWAITING_APPROVAL but has no PENDING attempt row"));
    }

    /**
     * ESCALATE_TO_MERCHANT is never executable — approving an escalation means the
     * merchant tells us what to actually do. NO_ACTION is rejected too: if the answer
     * is "do nothing", that's the reject endpoint, not an approval.
     */
    private RecoveryAction resolveAction(RecoveryCase recoveryCase, RecoveryAction requested) {
        RecoveryAction action = requested != null ? requested : recoveryCase.getSelectedStrategy();

        if (action == null || action == RecoveryAction.ESCALATE_TO_MERCHANT
                || action == RecoveryAction.NO_ACTION) {
            throw new IllegalArgumentException(
                    "approvedAction must be a concrete executable action (RETRY_PAYMENT, "
                            + "WAIT_AND_RETRY, SEND_PAYMENT_LINK, SEND_REMINDER or "
                            + "OFFER_SMALL_INCENTIVE). Received: " + action);
        }
        return action;
    }

    private void publish(ActivityEvent.Stage stage, RecoveryCase recoveryCase,
                         String message, Map<String, Object> data) {
        try {
            Map<String, Object> safe = new HashMap<>(data == null ? Map.of() : data);
            activityStreamPublisher.publish(ActivityEvent.of(
                    stage,
                    recoveryCase.getId(),
                    recoveryCase.getPayment() == null ? null : String.valueOf(recoveryCase.getPayment().getId()),
                    message,
                    safe));
        } catch (Exception ex) {
            log.debug("Activity publish failed (non-fatal): {}", ex.getMessage());
        }
    }
}