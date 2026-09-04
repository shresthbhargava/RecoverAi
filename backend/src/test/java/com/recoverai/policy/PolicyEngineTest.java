package com.recoverai.policy;

import com.recoverai.entity.Customer;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.service.PolicyRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock
    private PolicyRuleService policyRuleService;

    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        policyEngine = new PolicyEngine(policyRuleService);
    }

    @Test
    void shouldBlockRecoveryWhenMaximumAttemptsExceeded() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .amountAtRisk(new BigDecimal("15000.00"))
                .attemptCount(3)
                .lastContactAt(null)
                .build();

        Customer customer = Customer.builder()
                .name("Test Customer")
                .optedOut(false)
                .build();

        when(policyRuleService.getBoolean("STOP_AFTER_CUSTOMER_OPTOUT", true))
                .thenReturn(true);

        when(policyRuleService.getInt("MAX_RECOVERY_ATTEMPTS", 3))
                .thenReturn(3);

        PolicyDecision decision = policyEngine.evaluate(
                recoveryCase,
                customer,
                RecoveryAction.SEND_PAYMENT_LINK,
                null
        );

        assertFalse(decision.allowed());
        assertFalse(decision.requiresMerchantApproval());
        assertTrue(decision.reason().contains("Maximum recovery attempts"));
    }

    @Test
    void shouldAllowRecoveryWhenAttemptsAreBelowMaximum() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .amountAtRisk(new BigDecimal("15000.00"))
                .attemptCount(2)
                .lastContactAt(null)
                .build();

        Customer customer = Customer.builder()
                .name("Test Customer")
                .optedOut(false)
                .build();

        when(policyRuleService.getBoolean("STOP_AFTER_CUSTOMER_OPTOUT", true))
                .thenReturn(true);

        when(policyRuleService.getInt("MAX_RECOVERY_ATTEMPTS", 3))
                .thenReturn(3);

        when(policyRuleService.getInt("MIN_RETRY_INTERVAL_MINUTES", 30))
                .thenReturn(30);
        when(policyRuleService.getInt("NO_CUSTOMER_CONTACT_AFTER", 21))
                .thenReturn(21);

        PolicyDecision decision = policyEngine.evaluate(
                recoveryCase,
                customer,
                RecoveryAction.SEND_PAYMENT_LINK,
                null
        );

        assertTrue(decision.allowed());
        assertFalse(decision.requiresMerchantApproval());
        assertEquals("All policy checks passed", decision.reason());
    }

    @Test
    void shouldBlockRecoveryWhenCustomerOptedOut() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .amountAtRisk(new BigDecimal("5000.00"))
                .attemptCount(0)
                .build();

        Customer customer = Customer.builder()
                .name("Opted Out Customer")
                .optedOut(true)
                .build();

        when(policyRuleService.getBoolean("STOP_AFTER_CUSTOMER_OPTOUT", true))
                .thenReturn(true);

        PolicyDecision decision = policyEngine.evaluate(
                recoveryCase,
                customer,
                RecoveryAction.SEND_PAYMENT_LINK,
                null
        );

        assertFalse(decision.allowed());
        assertFalse(decision.requiresMerchantApproval());
        assertEquals("Customer has opted out of contact", decision.reason());
    }

    @Test
    void shouldAllowNoActionWithoutPolicyChecks() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .amountAtRisk(new BigDecimal("25.00"))
                .attemptCount(10)
                .build();

        Customer customer = Customer.builder()
                .name("Test Customer")
                .optedOut(true)
                .build();

        PolicyDecision decision = policyEngine.evaluate(
                recoveryCase,
                customer,
                RecoveryAction.NO_ACTION,
                null
        );

        assertTrue(decision.allowed());
        assertEquals("NO_ACTION requires no policy check", decision.reason());

        verifyNoInteractions(policyRuleService);
    }
}
