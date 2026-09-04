package com.recoverai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.agent.dto.CaseContext;
import com.recoverai.agent.dto.DiagnosisLlmOutput;
import com.recoverai.entity.enums.Diagnosis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisAgent {

    private final GrokClientService grokClientService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are the Diagnosis Agent inside RecoverAI, a payment-recovery system.
            Given a JSON description of one failed/abandoned payment and the customer's
            history, determine WHY revenue was likely lost.

            You MUST return ONLY a JSON object with this exact shape, nothing else:
            {
              "diagnosis": one of [TEMPORARY_BANK_FAILURE, INSUFFICIENT_FUNDS, USER_ABANDONMENT,
                                    PAYMENT_METHOD_FAILURE, TECHNICAL_TIMEOUT, LOW_RECOVERY_PROBABILITY, UNKNOWN],
              "confidence": number between 0 and 1,
              "reasoning": array of short strings, each referencing ONLY facts present in the input JSON
            }

            Rules:
            - Never invent facts not present in the input (no assumptions about bank policy, no guessing
              about causes not evidenced by the data).
            - If the input does not clearly support any specific diagnosis, return UNKNOWN with low confidence
              rather than guessing.
            - reasoning must be grounded, e.g. "Customer has 7 previous successful payments" is valid only
              if that number appears in the input.
            """;

    public AgentOutcome diagnose(CaseContext context) {
        try {
            String userPrompt = objectMapper.writeValueAsString(context);
            GrokClientService.LlmCallResult result = grokClientService.complete(SYSTEM_PROMPT, userPrompt);
            DiagnosisLlmOutput parsed = objectMapper.readValue(result.rawJson(), DiagnosisLlmOutput.class);

            Diagnosis diagnosis = validateDiagnosis(parsed.diagnosis());
            BigDecimal confidence = clampConfidence(parsed.confidence());

            return new AgentOutcome(diagnosis, confidence, parsed.reasoning(), result.model(), result.latencyMs(), false);

        } catch (Exception ex) {
            log.warn("Diagnosis Agent LLM call failed or returned invalid output, falling back: {}", ex.getMessage());
            return fallback(context);
        }
    }

    private Diagnosis validateDiagnosis(String raw) {
        try {
            return Diagnosis.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new com.recoverai.exception.InvalidAgentOutputException("Unknown diagnosis value from LLM: " + raw);
        }
    }

    private BigDecimal clampConfidence(BigDecimal confidence) {
        if (confidence == null) throw new com.recoverai.exception.InvalidAgentOutputException("Missing confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new com.recoverai.exception.InvalidAgentOutputException("Confidence out of range: " + confidence);
        }
        return confidence;
    }

    private AgentOutcome fallback(CaseContext context) {
        Diagnosis diagnosis;
        BigDecimal confidence;
        String reason;

        if (context.detectionCategory().equals("TEMPORARY_PAYMENT_FAILURE")) {
            diagnosis = Diagnosis.TEMPORARY_BANK_FAILURE;
            confidence = BigDecimal.valueOf(0.6);
            reason = "Rule-based fallback: failure reason indicates a temporary bank issue";
        } else if (context.detectionCategory().equals("INSUFFICIENT_FUNDS")) {
            diagnosis = Diagnosis.INSUFFICIENT_FUNDS;
            confidence = BigDecimal.valueOf(0.6);
            reason = "Rule-based fallback: failure reason indicates insufficient funds";
        } else if (context.eventType().equals("CHECKOUT_ABANDONED")) {
            diagnosis = Diagnosis.USER_ABANDONMENT;
            confidence = BigDecimal.valueOf(0.5);
            reason = "Rule-based fallback: event type is checkout abandonment";
        } else {
            diagnosis = Diagnosis.UNKNOWN;
            confidence = BigDecimal.valueOf(0.3);
            reason = "Rule-based fallback: LLM unavailable and no clear rule match";
        }

        return new AgentOutcome(diagnosis, confidence, List.of(reason), "fallback-heuristic", 0, true);
    }

    public record AgentOutcome(
            Diagnosis diagnosis,
            BigDecimal confidence,
            List<String> reasoning,
            String modelUsed,
            int latencyMs,
            boolean wasFallback
    ) {
    }
}