package com.recoverai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.agent.dto.CaseContext;
import com.recoverai.entity.enums.Diagnosis;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.service.RecoveryCostCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests the economic guardrail in StrategyAgent.buildOutcome — the rule that refuses
 * to spend more on a recovery than the recovery is worth.
 *
 * Only GrokClientService is mocked. ObjectMapper and RecoveryCostCalculator are the
 * real objects: the cost table IS the thing under test, so stubbing it would leave
 * the arithmetic unverified.
 *
 * Two paths reach the guardrail and both are covered, because the guardrail lives in
 * buildOutcome() which the LLM path and the fallback path both call:
 *   - LLM path:      stub complete() to return a chosen JSON body
 *   - fallback path: stub complete() to throw, as happens with no GROK_API_KEY
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrategyAgentEconomicGuardrailTest {

    @Mock
    private GrokClientService grokClientService;

    private StrategyAgent strategyAgent;

    @BeforeEach
    void setUp() {
        strategyAgent = new StrategyAgent(
                grokClientService,
                new ObjectMapper(),
                new RecoveryCostCalculator());
    }

    /** Mirrors what CaseContextBuilder produces; only amount and opt-out matter here. */
    private CaseContext contextWithAmount(String amount) {
        return new CaseContext(
                "pay_ref_1", new BigDecimal(amount), "INR", "PAYMENT_FAILED", "BANK_DECLINE",
                2, 1, new BigDecimal("50000"), false, 0, "MEDIUM", "UNCATEGORIZED_FAILURE");
    }

    private void llmRecommends(String action, String probability) {
        String json = "{\"recommendedAction\":\"" + action + "\","
                + "\"recoveryProbability\":" + probability + ","
                + "\"reasoning\":[\"LLM said so\"]}";
        when(grokClientService.complete(anyString(), anyString()))
                .thenReturn(new GrokClientService.LlmCallResult(json, "grok-test", 120));
    }

    private void llmUnavailable() {
        // What actually happens with no GROK_API_KEY: the WebClient call blows up.
        when(grokClientService.complete(anyString(), anyString()))
                .thenThrow(new RuntimeException("401 Unauthorized from Grok"));
    }

    // ---------------------------------------------------------------- the override

    @Test
    @DisplayName("SPEC: chasing Rs 25 with a Rs 10 payment link is downgraded to NO_ACTION")
    void negativeNetRecoveryIsOverriddenToNoAction() {
        // This is demo event 5, the case worth pointing at: the fallback heuristic
        // proposes SEND_PAYMENT_LINK for USER_ABANDONMENT regardless of value.
        llmUnavailable();

        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("25"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.6"));

        // 25 x 0.35 = 8.75 expected revenue, minus 10 cost = -1.25.
        assertThat(outcome.expectedRecoveryValue()).isEqualByComparingTo("8.75");
        assertThat(outcome.expectedNetRecovery()).isEqualByComparingTo("-1.25");

        assertThat(outcome.recommendedAction())
                .as("the agent overrode its own recommendation")
                .isEqualTo(RecoveryAction.NO_ACTION);
        assertThat(outcome.reasoning()).hasSize(1);
        assertThat(outcome.reasoning().get(0))
                .contains("Overridden by economic guardrail")
                .contains("SEND_PAYMENT_LINK");
    }

    @Test
    @DisplayName("The same override applies to an LLM recommendation, not just the fallback")
    void guardrailAlsoOverridesTheLlm() {
        // A reminder costs 15. On a Rs 20 payment at 50%, expected revenue is 10.
        llmRecommends("SEND_REMINDER", "0.5");

        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("20"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.9"));

        assertThat(outcome.wasFallback())
                .as("this is the LLM path — the guardrail is not a fallback-only rule")
                .isFalse();
        assertThat(outcome.expectedNetRecovery()).isEqualByComparingTo("-5.00");
        assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.NO_ACTION);
    }

    @Test
    @DisplayName("A worthwhile recovery is left alone")
    void positiveNetRecoveryIsKept() {
        llmUnavailable();

        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("12000"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.6"));

        // 12000 x 0.35 = 4200, minus 10 = 4190.
        assertThat(outcome.expectedNetRecovery()).isEqualByComparingTo("4190.00");
        assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.SEND_PAYMENT_LINK);
        assertThat(outcome.reasoning()).noneMatch(r -> r.contains("guardrail"));
    }

    @Test
    @DisplayName("Breaking exactly even is allowed — the test is strictly negative")
    void exactlyBreakingEvenIsAllowed() {
        // 28.58 x 0.35 = 10.003, rounds HALF_UP to 10.00, cost 10, net exactly 0.
        // The check is compareTo(ZERO) < 0, so zero passes. Boundary, deliberately.
        llmUnavailable();

        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("28.58"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.6"));

        assertThat(outcome.expectedNetRecovery()).isEqualByComparingTo("0.00");
        assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.SEND_PAYMENT_LINK);
    }

    @Test
    @DisplayName("The cheap bank-side retry survives on amounts where a link would not")
    void cheaperActionSurvivesOnSmallAmounts() {
        // WAIT_AND_RETRY costs 5 at probability 0.65, so it clears on much less.
        llmUnavailable();

        StrategyAgent.AgentOutcome cheap = strategyAgent.recommend(
                contextWithAmount("25"), Diagnosis.TEMPORARY_BANK_FAILURE, new BigDecimal("0.6"));

        // 25 x 0.65 = 16.25 - 5 = 11.25. Same amount as the overridden case above.
        assertThat(cheap.recommendedAction()).isEqualTo(RecoveryAction.WAIT_AND_RETRY);
        assertThat(cheap.expectedNetRecovery()).isEqualByComparingTo("11.25");
    }

    @Test
    @DisplayName("NO_ACTION is never 'overridden' to itself, even at zero value")
    void noActionIsExemptFromTheGuardrail() {
        llmUnavailable();

        // Confidence below 0.5 makes the fallback choose NO_ACTION at probability 0.
        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("800"), Diagnosis.UNKNOWN, new BigDecimal("0.3"));

        assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.NO_ACTION);
        assertThat(outcome.recoveryCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.reasoning())
                .as("the heuristic reason must survive, not be replaced by a guardrail message")
                .noneMatch(r -> r.contains("guardrail"));
    }

    /*
     * Documents a rough edge rather than asserting it is right.
     *
     * cost is computed from the ORIGINAL action before the override, and buildOutcome
     * returns that same cost alongside the downgraded action. So an overridden outcome
     * reports recoveryCost = 10 while recommending NO_ACTION, which really costs nothing.
     *
     * It does not corrupt any money figure that matters: BatchProcessorService:147 only
     * adds cost to the case when the attempt was not blocked, and RecoveryExecutor is
     * handed the cost of the action it actually runs. But the AgentDecision row shown in
     * the UI carries the pre-override cost, so the number is the cost of the road not
     * taken. Recording that here so a later "why does a NO_ACTION cost 10" is a two-minute
     * question instead of an hour.
     */
    @Test
    @DisplayName("KNOWN QUIRK: an overridden outcome still reports the rejected action's cost")
    void overriddenOutcomeReportsTheRejectedActionsCost() {
        llmUnavailable();

        StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                contextWithAmount("25"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.6"));

        assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.NO_ACTION);
        assertThat(outcome.recoveryCost())
                .as("cost of the SEND_PAYMENT_LINK that was refused, not of NO_ACTION")
                .isEqualByComparingTo("10");
        assertThat(outcome.recoveryProbability())
                .as("likewise the probability belongs to the rejected action")
                .isEqualByComparingTo("0.35");
    }

    // ---------------------------------------------------------------- fallback trigger

    @Nested
    @DisplayName("Falling back to heuristics")
    class Fallback {

        @Test
        @DisplayName("An unreachable LLM falls back and says so")
        void unreachableLlmIsFlagged() {
            llmUnavailable();

            StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                    contextWithAmount("4500"), Diagnosis.TEMPORARY_BANK_FAILURE, new BigDecimal("0.6"));

            assertThat(outcome.wasFallback())
                    .as("a fallback must never be presented as a real model decision")
                    .isTrue();
            assertThat(outcome.modelUsed()).isEqualTo("fallback-heuristic");
            assertThat(outcome.latencyMs()).isZero();
            assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.WAIT_AND_RETRY);
        }

        @Test
        @DisplayName("An action outside the fixed list falls back instead of being trusted")
        void inventedActionFallsBack() {
            // The prompt fixes seven allowed values; an LLM can still return anything.
            llmRecommends("REFUND_EVERYTHING", "0.9");

            StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                    contextWithAmount("4500"), Diagnosis.TEMPORARY_BANK_FAILURE, new BigDecimal("0.6"));

            assertThat(outcome.wasFallback()).isTrue();
            assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.WAIT_AND_RETRY);
        }

        @Test
        @DisplayName("A probability outside 0..1 falls back rather than being clamped silently")
        void outOfRangeProbabilityFallsBack() {
            llmRecommends("SEND_PAYMENT_LINK", "1.4");

            StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                    contextWithAmount("4500"), Diagnosis.USER_ABANDONMENT, new BigDecimal("0.6"));

            assertThat(outcome.wasFallback()).isTrue();
            assertThat(outcome.recoveryProbability()).isEqualByComparingTo("0.35");
        }

        @Test
        @DisplayName("An opted-out customer gets NO_ACTION from the fallback before anything else")
        void optedOutCustomerGetsNoAction() {
            llmUnavailable();

            CaseContext optedOut = new CaseContext(
                    "pay_ref_2", new BigDecimal("9000"), "INR", "PAYMENT_FAILED", "BANK_DECLINE",
                    0, 3, BigDecimal.ZERO, true, 1, "HIGH", "UNCATEGORIZED_FAILURE");

            StrategyAgent.AgentOutcome outcome = strategyAgent.recommend(
                    optedOut, Diagnosis.TEMPORARY_BANK_FAILURE, new BigDecimal("0.9"));

            assertThat(outcome.recommendedAction()).isEqualTo(RecoveryAction.NO_ACTION);
            assertThat(outcome.reasoning()).hasSize(1);
            assertThat(outcome.reasoning().get(0)).contains("opted out");
        }
    }
}
