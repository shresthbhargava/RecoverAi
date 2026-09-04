package com.recoverai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.agent.dto.CaseContext;
import com.recoverai.agent.dto.StrategyLlmOutput;
import com.recoverai.entity.enums.Diagnosis;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.service.RecoveryCostCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyAgent {

    private final GrokClientService grokClientService;
    private final ObjectMapper objectMapper;
    private final RecoveryCostCalculator recoveryCostCalculator;

    private static final String SYSTEM_PROMPT = """
            You are the Strategy Agent inside RecoverAI. Given a diagnosed payment-recovery
            case, recommend ONE action from this fixed list — never anything outside it:
            [RETRY_PAYMENT, WAIT_AND_RETRY, SEND_PAYMENT_LINK, SEND_REMINDER,
             OFFER_SMALL_INCENTIVE, ESCALATE_TO_MERCHANT, NO_ACTION]

            Return ONLY a JSON object:
            {
              "recommendedAction": one of the actions above,
              "recoveryProbability": number between 0 and 1 (your estimate of the chance
                                      this action recovers the payment),
              "reasoning": array of short strings grounded only in the provided input
            }

            Guidance:
            - If the diagnosis confidence is low or the case is UNKNOWN, prefer NO_ACTION or
              ESCALATE_TO_MERCHANT over guessing.
            - If prior recovery attempts are already high relative to typical limits, favor
              ESCALATE_TO_MERCHANT or NO_ACTION over another retry.
            - If the customer has opted out, you MUST recommend NO_ACTION.
            - Do not recommend contact-based actions (SEND_REMINDER, SEND_PAYMENT_LINK,
              OFFER_SMALL_INCENTIVE) for very low-value transactions where recovery cost
              likely exceeds recoverable value — prefer NO_ACTION in that case instead.
            """;

    public AgentOutcome recommend(CaseContext context, Diagnosis diagnosis, BigDecimal diagnosisConfidence) {
        try {
            String userPrompt = objectMapper.writeValueAsString(new PromptInput(context, diagnosis.name(), diagnosisConfidence));
            GrokClientService.LlmCallResult result = grokClientService.complete(SYSTEM_PROMPT, userPrompt);
            StrategyLlmOutput parsed = objectMapper.readValue(result.rawJson(), StrategyLlmOutput.class);

            RecoveryAction action = validateAction(parsed.recommendedAction());
            BigDecimal probability = clampProbability(parsed.recoveryProbability());

            return buildOutcome(context, action, probability, parsed.reasoning(), result.model(), result.latencyMs(), false);

        } catch (Exception ex) {
            log.warn("Strategy Agent LLM call failed or returned invalid output, falling back: {}", ex.getMessage());
            return fallback(context, diagnosis, diagnosisConfidence);
        }
    }

    private AgentOutcome buildOutcome(CaseContext context, RecoveryAction action, BigDecimal probability,
                                      List<String> reasoning, String model, int latencyMs, boolean wasFallback) {
        BigDecimal cost = recoveryCostCalculator.costOf(action);
        BigDecimal expectedRevenue = context.amount().multiply(probability).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedNetRecovery = expectedRevenue.subtract(cost);

        RecoveryAction finalAction = action;
        List<String> finalReasoning = reasoning;

        if (action != RecoveryAction.NO_ACTION && expectedNetRecovery.compareTo(BigDecimal.ZERO) < 0) {
            finalAction = RecoveryAction.NO_ACTION;
            finalReasoning = List.of(
                    "Overridden by economic guardrail: expected net recovery ("
                            + expectedNetRecovery + ") is negative for proposed action " + action
            );
        }

        return new AgentOutcome(finalAction, probability, expectedRevenue, cost, expectedNetRecovery,
                finalReasoning, model, latencyMs, wasFallback);
    }

    private RecoveryAction validateAction(String raw) {
        try {
            return RecoveryAction.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new com.recoverai.exception.InvalidAgentOutputException("Unknown action value from LLM: " + raw);
        }
    }

    private BigDecimal clampProbability(BigDecimal probability) {
        if (probability == null) throw new com.recoverai.exception.InvalidAgentOutputException("Missing recoveryProbability");
        if (probability.compareTo(BigDecimal.ZERO) < 0 || probability.compareTo(BigDecimal.ONE) > 0) {
            throw new com.recoverai.exception.InvalidAgentOutputException("Probability out of range: " + probability);
        }
        return probability;
    }

    private AgentOutcome fallback(CaseContext context, Diagnosis diagnosis, BigDecimal diagnosisConfidence) {
        if (context.customerOptedOut()) {
            return buildOutcome(context, RecoveryAction.NO_ACTION, BigDecimal.ZERO,
                    List.of("Rule-based fallback: customer has opted out of contact"), "fallback-heuristic", 0, true);
        }

        RecoveryAction action;
        BigDecimal probability;

        if (diagnosisConfidence.compareTo(BigDecimal.valueOf(0.5)) < 0 || diagnosis == Diagnosis.UNKNOWN) {
            action = RecoveryAction.NO_ACTION;
            probability = BigDecimal.ZERO;
        } else if (diagnosis == Diagnosis.TEMPORARY_BANK_FAILURE || diagnosis == Diagnosis.TECHNICAL_TIMEOUT) {
            action = RecoveryAction.WAIT_AND_RETRY;
            probability = BigDecimal.valueOf(0.65);
        } else if (diagnosis == Diagnosis.USER_ABANDONMENT) {
            action = RecoveryAction.SEND_PAYMENT_LINK;
            probability = BigDecimal.valueOf(0.35);
        } else {
            action = RecoveryAction.NO_ACTION;
            probability = BigDecimal.ZERO;
        }

        return buildOutcome(context, action, probability,
                List.of("Rule-based fallback: selected via diagnosis-to-action heuristic map"),
                "fallback-heuristic", 0, true);
    }

    private record PromptInput(CaseContext context, String diagnosis, BigDecimal diagnosisConfidence) {
    }

    public record AgentOutcome(
            RecoveryAction recommendedAction,
            BigDecimal recoveryProbability,
            BigDecimal expectedRecoveryValue,
            BigDecimal recoveryCost,
            BigDecimal expectedNetRecovery,
            List<String> reasoning,
            String modelUsed,
            int latencyMs,
            boolean wasFallback
    ) {
    }
}