package com.recoverai.service;

import com.recoverai.audit.AuditService;
import com.recoverai.entity.Customer;
import com.recoverai.entity.Payment;
import com.recoverai.entity.RecoveryAttempt;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.RecoveryAction;
import com.recoverai.policy.PolicyDecision;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import com.recoverai.razorpay.RazorpayClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryExecutorTest {

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

    @BeforeEach
    void setUp() {
        recoveryExecutor = new RecoveryExecutor(
                razorpayClientService,
                recoveryAttemptRepository,
                recoveryCaseRepository,
                auditService,
                activityStreamPublisher
        );
    }

    @Test
    void blockedPolicyMustNotExecuteRecoveryAction() {
        Payment payment = new Payment();
        payment.setId(java.util.UUID.randomUUID());

        Customer customer = Customer.builder()
                .name("Test Customer")
                .email("test@example.com")
                .phone("9999999999")
                .optedOut(false)
                .build();

        RecoveryCase recoveryCase = RecoveryCase.builder()
                .payment(payment)
                .customer(customer)
                .amountAtRisk(new BigDecimal("15000.00"))
                .attemptCount(3)
                .recoveryCost(BigDecimal.ZERO)
                .build();

        PolicyDecision blockedDecision =
                PolicyDecision.block("Maximum recovery attempts (3) exceeded");

        RecoveryAttempt blockedAttempt = RecoveryAttempt.builder()
                .recoveryCase(recoveryCase)
                .action(RecoveryAction.SEND_PAYMENT_LINK)
                .attemptNumber(4)
                .status(AttemptStatus.BLOCKED)
                .amountRecovered(BigDecimal.ZERO)
                .build();

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class)))
                .thenReturn(blockedAttempt);

        RecoveryAttempt result = recoveryExecutor.execute(
                recoveryCase,
                RecoveryAction.SEND_PAYMENT_LINK,
                new BigDecimal("0.80"),
                new BigDecimal("30.00"),
                blockedDecision
        );

        assertNotNull(result);
        assertEquals(AttemptStatus.BLOCKED, result.getStatus());
        assertEquals(CaseStatus.BLOCKED, recoveryCase.getStatus());

        verify(razorpayClientService, never())
                .createPaymentLink(any(), any(), any(), any(), any(), any());

        verify(recoveryAttemptRepository)
                .save(any(RecoveryAttempt.class));

        verify(recoveryCaseRepository)
                .save(recoveryCase);

        verify(auditService)
                .log(any(), any(), eq("ACTION_BLOCKED"), any(), any(), any(), any());
    }
}

