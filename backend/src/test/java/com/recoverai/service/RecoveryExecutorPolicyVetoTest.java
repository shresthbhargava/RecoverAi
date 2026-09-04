package com.recoverai.service;

import com.recoverai.audit.AuditService;
import com.recoverai.entity.Customer;
import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.Actor;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.policy.PolicyDecision;
import com.recoverai.razorpay.RazorpayClientService;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The test case the spec names explicitly:
 *
 *     "AI proposes -> Policy blocks -> action must NOT execute"
 *
 * The important assertion in this class is not that the attempt row says BLOCKED.
 * It is verifyNoInteractions(razorpayClientService) — proof that the side effect
 * never happened, not merely that it was labelled as prevented. A system that
 * charged the customer and then wrote "BLOCKED" in its audit log would satisfy the
 * weaker assertion and fail the requirement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoveryExecutorPolicyVetoTest {

    @Mock
    private RazorpayClientService razorpayClientService;
    @Mock
    private RecoveryAttemptRepository recoveryAttemptRepository;
    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ActivityStreamPublisher activityStreamPublisher;

    private RecoveryExecutor recoveryExecutor;
    private RecoveryCase recoveryCase;

    @BeforeEach
    void setUp() {
        recoveryExecutor = new RecoveryExecutor(
                razorpayClientService,
                recoveryAttemptRepository,
                recoveryCaseRepository,
                auditService,
                activityStreamPublisher);

        Customer customer = Customer.builder()
                .email("blocked@example.com")
                .name("Blocked Customer")
                .optedOut(false)
                .build();

        Payment payment = Payment.builder()
                .customer(customer)
                .amount(new BigDecimal("15000"))
                .currency("INR")
                .build();

        recoveryCase = RecoveryCase.builder()
                .payment(payment)
                .customer(customer)
                .amountAtRisk(new BigDecimal("15000"))
                .status(CaseStatus.IN_PROGRESS)
                .attemptCount(0)
                .recoveryCost(BigDecimal.ZERO)
                .build();
        recoveryCase.setId(UUID.randomUUID());

        // save() returns its argument, so assertions can read the object the code built.
        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryCaseRepository.save(any(RecoveryCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("SPEC: AI proposes SEND_PAYMENT_LINK, policy blocks, Razorpay is never called")
    void blockedActionMustNotExecute() {
        // The AI has proposed a real, customer-facing, money-moving action.
        RecoveryAction aiProposal = RecoveryAction.SEND_PAYMENT_LINK;

        // The Policy Engine refuses it.
        PolicyDecision veto = PolicyDecision.block("Maximum recovery attempts (0) exceeded");

        RecoveryAttempt attempt = recoveryExecutor.execute(
                recoveryCase, aiProposal, new BigDecimal("0.35"), new BigDecimal("10"), veto);

        // THE requirement: no external side effect of any kind.
        verifyNoInteractions(razorpayClientService);

        // And the refusal is recorded honestly rather than silently dropped.
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.BLOCKED);
        assertThat(attempt.getAction())
                .as("the proposed action is preserved, so the audit trail shows what was refused")
                .isEqualTo(aiProposal);
        assertThat(attempt.getIsRealApiAction()).isFalse();
        assertThat(attempt.getAmountRecovered()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(attempt.getResult()).isEqualTo("Maximum recovery attempts (0) exceeded");

        assertThat(recoveryCase.getStatus()).isEqualTo(CaseStatus.BLOCKED);
        assertThat(recoveryCase.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("The block is attributed to POLICY_ENGINE in the audit log, not to the agents")
    void blockIsAttributedToThePolicyEngine() {
        PolicyDecision veto = PolicyDecision.block("Customer has opted out of contact");

        recoveryExecutor.execute(
                recoveryCase, RecoveryAction.SEND_REMINDER, new BigDecimal("0.4"), new BigDecimal("15"), veto);

        verify(auditService).log(
                eq(recoveryCase),
                eq(Actor.POLICY_ENGINE),
                eq("ACTION_BLOCKED"),
                anyString(),
                eq(CaseStatus.BLOCKED.name()),
                eq("Customer has opted out of contact"),
                any());
    }

    @ParameterizedTest
    @EnumSource(value = RecoveryAction.class,
            names = {"RETRY_PAYMENT", "WAIT_AND_RETRY", "SEND_PAYMENT_LINK"})
    @DisplayName("Every real-API action is stopped before the API call")
    void allRealApiActionsAreStoppedByAVeto(RecoveryAction realApiAction) {
        // These three are the only actions that would otherwise reach Razorpay.
        PolicyDecision veto = PolicyDecision.block("Policy says no");

        RecoveryAttempt attempt = recoveryExecutor.execute(
                recoveryCase, realApiAction, new BigDecimal("0.5"), new BigDecimal("5"), veto);

        verifyNoInteractions(razorpayClientService);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.BLOCKED);
        assertThat(attempt.getIsRealApiAction())
                .as("a blocked attempt never touched the API, so it must not claim it did")
                .isFalse();
    }

    @Test
    @DisplayName("A blocked attempt records zero recovered revenue")
    void blockedAttemptRecoversNothing() {
        PolicyDecision veto = PolicyDecision.block("Batch recovery cost budget (50000) would be exceeded");

        ArgumentCaptor<RecoveryAttempt> captor = ArgumentCaptor.forClass(RecoveryAttempt.class);

        recoveryExecutor.execute(
                recoveryCase, RecoveryAction.RETRY_PAYMENT, new BigDecimal("0.6"), new BigDecimal("5"), veto);

        verify(recoveryAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getAmountRecovered()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /*
     * Documents actual current behaviour rather than intended behaviour.
     *
     * PolicyDecision.requireApproval() reports allowed=false, and execute() tests
     * !allowed() before it tests requiresMerchantApproval(). So an approval request
     * takes the recordBlocked path: the attempt lands BLOCKED and the case BLOCKED,
     * never PENDING/AWAITING_APPROVAL.
     *
     * That means the requiresMerchantApproval branch inside execute() is unreachable
     * from PolicyEngine.evaluate(), and cases never park in AWAITING_APPROVAL from a
     * batch run — so the approve/reject endpoints have nothing to act on unless a case
     * is put there by hand.
     *
     * This test is written to pass against the code as it stands. If the ordering is
     * ever corrected, this test will fail loudly, which is the point: the decision
     * should be deliberate, not silent. Nothing about the veto requirement depends on
     * it — a blocked action still does not execute either way.
     */
    @Test
    @DisplayName("KNOWN GAP: an approval request is currently recorded as BLOCKED, not AWAITING_APPROVAL")
    void approvalRequestCurrentlyTakesTheBlockedPath() {
        PolicyDecision needsApproval =
                PolicyDecision.requireApproval("Incentive amount (750) exceeds auto-approval limit (500)");

        assertThat(needsApproval.allowed())
                .as("requireApproval reports allowed=false, which is why the ordering matters")
                .isFalse();

        RecoveryAttempt attempt = recoveryExecutor.execute(
                recoveryCase, RecoveryAction.OFFER_SMALL_INCENTIVE,
                new BigDecimal("0.5"), new BigDecimal("50"), needsApproval);

        assertThat(attempt.getStatus())
                .as("currently BLOCKED; would be PENDING if the approval branch were reachable")
                .isEqualTo(AttemptStatus.BLOCKED);
        assertThat(recoveryCase.getStatus()).isEqualTo(CaseStatus.BLOCKED);

        // Either way, the money-side guarantee holds.
        verifyNoInteractions(razorpayClientService);
    }

    @Test
    @DisplayName("An allowed NO_ACTION also makes no API call")
    void noActionMakesNoApiCall() {
        PolicyDecision allowed = PolicyDecision.allow("NO_ACTION requires no policy check");

        RecoveryAttempt attempt = recoveryExecutor.execute(
                recoveryCase, RecoveryAction.NO_ACTION, BigDecimal.ZERO, BigDecimal.ZERO, allowed);

        verifyNoInteractions(razorpayClientService);
        assertThat(attempt.getStatus()).isNotEqualTo(AttemptStatus.SUCCEEDED);
    }
}
