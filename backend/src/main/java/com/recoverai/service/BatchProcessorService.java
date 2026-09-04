package com.recoverai.service;

import com.recoverai.agent.CaseContextBuilder;
import com.recoverai.agent.DetectionAgent;
import com.recoverai.agent.DiagnosisAgent;
import com.recoverai.agent.StrategyAgent;
import com.recoverai.agent.dto.CaseContext;
import com.recoverai.agent.dto.DetectionResult;
import com.recoverai.audit.AuditService;
import com.recoverai.dto.request.BatchProcessRequest;
import com.recoverai.dto.request.PaymentEventRequest;
import com.recoverai.dto.response.BatchResultResponse;
import com.recoverai.entity.*;
import com.recoverai.entity.enums.*;
import com.recoverai.policy.PolicyDecision;
import com.recoverai.policy.PolicyEngine;
import com.recoverai.realtime.ActivityEvent;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.AgentDecisionRepository;
import com.recoverai.repository.PaymentRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The orchestrator — implements DETECT -> DIAGNOSE -> DECIDE -> VALIDATE ->
 * EXECUTE -> MEASURE -> AUDIT as one method, one event at a time.
 *
 * Every agent output is persisted immediately rather than batched to the end,
 * so a crash mid-batch still leaves a readable partial trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessorService {

    private final CustomerService customerService;
    private final PaymentRepository paymentRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AgentDecisionRepository agentDecisionRepository;

    private final DetectionAgent detectionAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final StrategyAgent strategyAgent;
    private final PolicyEngine policyEngine;
    private final RecoveryExecutor recoveryExecutor;
    private final CaseContextBuilder caseContextBuilder;
    private final AuditService auditService;
    private final ActivityStreamPublisher activityStreamPublisher;

    @Transactional
    public BatchResultResponse processBatch(BatchProcessRequest request) {
        UUID batchId = UUID.randomUUID();
        int totalEvents = request.events().size();

        auditService.logSystem("BATCH_STARTED", "Processing " + totalEvents + " events",
                Map.of("batchId", batchId.toString()));

        publish(ActivityEvent.Stage.BATCH_STARTED, null, null,
                "Batch started — " + totalEvents + " events queued",
                Map.of("batchId", batchId.toString(), "totalEvents", totalEvents));

        int transactionsAnalyzed = 0;
        int actionsBlocked = 0;
        int successfulRecoveries = 0;
        int recoveryActionsProposed = 0;
        BigDecimal revenueAtRisk = BigDecimal.ZERO;
        BigDecimal revenueRecovered = BigDecimal.ZERO;
        BigDecimal cumulativeCost = BigDecimal.ZERO;

        for (PaymentEventRequest event : request.events()) {
            transactionsAnalyzed++;
            RecoveryCase recoveryCase = ingestAndDetect(event, transactionsAnalyzed, totalEvents);
            if (recoveryCase == null) continue;

            revenueAtRisk = revenueAtRisk.add(recoveryCase.getAmountAtRisk());

            CaseContext context = caseContextBuilder.build(recoveryCase);

            var diagnosisOutcome = diagnosisAgent.diagnose(context);
            recoveryCase.setDiagnosis(diagnosisOutcome.diagnosis());
            recoveryCase.setDiagnosisConfidence(diagnosisOutcome.confidence());
            recordDecision(recoveryCase, AgentType.DIAGNOSIS, context, diagnosisOutcome.diagnosis().name(),
                    diagnosisOutcome.confidence(), diagnosisOutcome.reasoning(), diagnosisOutcome.modelUsed(), diagnosisOutcome.latencyMs());

            Map<String, Object> diagnosisData = new HashMap<>();
            diagnosisData.put("diagnosis", diagnosisOutcome.diagnosis().name());
            diagnosisData.put("confidence", diagnosisOutcome.confidence());
            diagnosisData.put("model", diagnosisOutcome.modelUsed());
            diagnosisData.put("latencyMs", diagnosisOutcome.latencyMs());
            diagnosisData.put("wasFallback", diagnosisOutcome.wasFallback());
            publish(ActivityEvent.Stage.DIAGNOSED, recoveryCase.getId(), paymentRefOf(recoveryCase),
                    "Diagnosed " + diagnosisOutcome.diagnosis().name()
                            + " (confidence " + diagnosisOutcome.confidence() + ")",
                    diagnosisData);

            var strategyOutcome = strategyAgent.recommend(context, diagnosisOutcome.diagnosis(), diagnosisOutcome.confidence());
            recoveryCase.setSelectedStrategy(strategyOutcome.recommendedAction());
            recoveryCase.setRecoveryProbability(strategyOutcome.recoveryProbability());
            recoveryCase.setExpectedRecoveryValue(strategyOutcome.expectedRecoveryValue());
            recordDecision(recoveryCase, AgentType.STRATEGY, context, strategyOutcome.recommendedAction().name(),
                    strategyOutcome.recoveryProbability(), strategyOutcome.reasoning(), strategyOutcome.modelUsed(), strategyOutcome.latencyMs());

            Map<String, Object> strategyData = new HashMap<>();
            strategyData.put("action", strategyOutcome.recommendedAction().name());
            strategyData.put("recoveryProbability", strategyOutcome.recoveryProbability());
            strategyData.put("expectedRecoveryValue", strategyOutcome.expectedRecoveryValue());
            strategyData.put("recoveryCost", strategyOutcome.recoveryCost());
            strategyData.put("expectedNetRecovery", strategyOutcome.expectedNetRecovery());
            strategyData.put("wasFallback", strategyOutcome.wasFallback());
            publish(ActivityEvent.Stage.STRATEGY_SELECTED, recoveryCase.getId(), paymentRefOf(recoveryCase),
                    "Strategy " + strategyOutcome.recommendedAction().name()
                            + " — expected net " + strategyOutcome.expectedNetRecovery(),
                    strategyData);

            if (strategyOutcome.recommendedAction() != RecoveryAction.NO_ACTION) {
                recoveryActionsProposed++;
            }

            PolicyDecision policyDecision = policyEngine.evaluate(
                    recoveryCase, recoveryCase.getCustomer(), strategyOutcome.recommendedAction(), strategyOutcome.recoveryCost());

            if (policyDecision.allowed() && !policyDecision.requiresMerchantApproval()) {
                PolicyDecision budgetCheck = policyEngine.checkBatchCostBudget(cumulativeCost, strategyOutcome.recoveryCost());
                if (!budgetCheck.allowed()) {
                    policyDecision = budgetCheck;
                }
            }

            RecoveryAttempt attempt = recoveryExecutor.execute(
                    recoveryCase, strategyOutcome.recommendedAction(),
                    strategyOutcome.recoveryProbability(), strategyOutcome.recoveryCost(), policyDecision);

            if (attempt.getStatus() == AttemptStatus.BLOCKED) actionsBlocked++;
            if (attempt.getStatus() == AttemptStatus.SUCCEEDED) {
                successfulRecoveries++;
                revenueRecovered = revenueRecovered.add(attempt.getAmountRecovered());
            }
            if (attempt.getStatus() != AttemptStatus.BLOCKED) {
                cumulativeCost = cumulativeCost.add(strategyOutcome.recoveryCost());
            }

            customerService.recordPaymentOutcome(recoveryCase.getCustomer(), attempt.getStatus() == AttemptStatus.SUCCEEDED);
        }

        BigDecimal netRecovered = revenueRecovered.subtract(cumulativeCost);

        auditService.logSystem("BATCH_COMPLETED",
                "Processed " + transactionsAnalyzed + " events, " + successfulRecoveries + " recovered",
                Map.of("batchId", batchId.toString(), "revenueRecovered", revenueRecovered));

        Map<String, Object> summary = new HashMap<>();
        summary.put("batchId", batchId.toString());
        summary.put("transactionsAnalyzed", transactionsAnalyzed);
        summary.put("successfulRecoveries", successfulRecoveries);
        summary.put("actionsBlocked", actionsBlocked);
        summary.put("revenueRecovered", revenueRecovered);
        summary.put("recoveryCost", cumulativeCost);
        summary.put("netRecovered", netRecovered);
        publish(ActivityEvent.Stage.BATCH_COMPLETED, null, null,
                "Batch complete — " + successfulRecoveries + "/" + transactionsAnalyzed
                        + " recovered, net " + netRecovered,
                summary);

        return new BatchResultResponse(batchId, transactionsAnalyzed, revenueAtRisk, recoveryActionsProposed,
                actionsBlocked, successfulRecoveries, revenueRecovered, cumulativeCost, netRecovered);
    }

    private RecoveryCase ingestAndDetect(PaymentEventRequest event, int index, int total) {
        String email = event.customerEmail() != null ? event.customerEmail() : event.customerRef();
        Customer customer = customerService.getOrCreate(email, event.customerName(), event.customerPhone());

        Payment payment = paymentRepository.save(Payment.builder()
                .customer(customer)
                .amount(event.amount())
                .currency(event.currency() == null ? "INR" : event.currency())
                .status(PaymentStatus.FAILED)
                .eventType(event.eventType())
                .failureReason(event.failureReason())
                .paymentMethod(event.paymentMethod())
                .build());

        DetectionResult detection = detectionAgent.evaluate(event.eventType(), event.amount(), event.failureReason());
        if (!detection.revenueAtRisk()) return null;

        RecoveryCase recoveryCase = recoveryCaseRepository.save(RecoveryCase.builder()
                .payment(payment)
                .customer(customer)
                .amountAtRisk(event.amount())
                .status(CaseStatus.OPEN)
                .build());

        recordDecision(recoveryCase, AgentType.DETECTION, null,
                detection.category(), null,
                List.of("severity=" + detection.severity(), "category=" + detection.category()),
                "rule-based", 0);

        Map<String, Object> data = new HashMap<>();
        data.put("index", index);
        data.put("total", total);
        data.put("amountAtRisk", event.amount());
        data.put("severity", detection.severity());
        data.put("category", detection.category());
        data.put("eventType", event.eventType().name());
        publish(ActivityEvent.Stage.DETECTED, recoveryCase.getId(), String.valueOf(payment.getId()),
                "Processing " + index + "/" + total + " — " + event.amount()
                        + " at risk (" + detection.category() + ")",
                data);

        return recoveryCase;
    }

    private void recordDecision(RecoveryCase recoveryCase, AgentType agentType, Object inputContext, String decision,
                                BigDecimal confidence, List<String> reasoning, String model, int latencyMs) {
        AgentDecision record = AgentDecision.builder()
                .recoveryCase(recoveryCase)
                .agentType(agentType)
                .inputSummary(inputContext != null ? inputContext.toString() : null)
                .decision(decision)
                .confidence(confidence)
                .reasoning(reasoning)
                .llmModel(model)
                .latencyMs(latencyMs)
                .build();
        agentDecisionRepository.save(record);
    }

    private String paymentRefOf(RecoveryCase recoveryCase) {
        return recoveryCase.getPayment() == null ? null : String.valueOf(recoveryCase.getPayment().getId());
    }

    /** Never let a dead browser connection break a batch run. */
    private void publish(ActivityEvent.Stage stage, UUID caseId, String paymentRef,
                         String message, Map<String, Object> data) {
        try {
            activityStreamPublisher.publish(ActivityEvent.of(stage, caseId, paymentRef, message,
                    data == null ? Map.of() : new HashMap<>(data)));
        } catch (Exception ex) {
            log.debug("Activity publish failed (non-fatal): {}", ex.getMessage());
        }
    }
}
