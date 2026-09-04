import type {
  AnalyticsSummary,
  RecoveryCaseSummary,
  RecoveryCaseDetail,
  AgentDecision,
  PolicyRule,
  WebhookEvent,
  HealthResponse,
  LiveActivityEvent
} from './types';


export const mockAnalyticsSummary: AnalyticsSummary = {
  totalEventsProcessed: 142,
  revenueAtRisk: 34825,
  recoverableCases: 5,
  recoveryAttempts: 4,
  successfulRecoveries: 2,
  failedRecoveries: 1,
  blockedActions: 2,
  revenueRecovered: 15000,
  recoveryRate: 43.1,
  recoveryCost: 30,
  netRecoveredRevenue: 14970
};

export const mockCaseSummaries: RecoveryCaseSummary[] = [
  {
    id: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    customerName: 'Rahul Sharma',
    amountAtRisk: 15000,
    status: 'RECOVERED',
    diagnosis: 'TEMPORARY_BANK_OUTAGE',
    diagnosisConfidence: 0.91,
    selectedStrategy: 'WAIT_AND_RETRY',
    attemptCount: 1,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    resolvedAt: new Date(Date.now() - 3600000 * 1.5).toISOString()
  },
  {
    id: '5f8e31a2-90cd-4b11-88f2-110294819a01',
    customerName: 'Priya Mehta',
    amountAtRisk: 12000,
    status: 'EXECUTING',
    diagnosis: 'PAYMENT_TIMEOUT',
    diagnosisConfidence: 0.84,
    selectedStrategy: 'SEND_PAYMENT_LINK',
    attemptCount: 1,
    createdAt: new Date(Date.now() - 3600000 * 4).toISOString(),
    resolvedAt: null
  },
  {
    id: '9a12c4bf-7019-482f-b12a-8819204910ef',
    customerName: 'Vikram Malhotra',
    amountAtRisk: 7825,
    status: 'POLICY_BLOCKED',
    diagnosis: 'INSUFFICIENT_FUNDS',
    diagnosisConfidence: 0.95,
    selectedStrategy: 'SEND_PAYMENT_LINK',
    attemptCount: 3,
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString(),
    resolvedAt: new Date(Date.now() - 3600000 * 6).toISOString()
  },
  {
    id: '1d49e8a0-21ba-411a-9f5b-99120481829a',
    customerName: 'Ananya Roy',
    amountAtRisk: 4500,
    status: 'AWAITING_APPROVAL',
    diagnosis: 'CARD_LIMIT_EXCEEDED',
    diagnosisConfidence: 0.79,
    selectedStrategy: 'OFFER_INCENTIVE_RETRY',
    attemptCount: 2,
    createdAt: new Date(Date.now() - 3600000 * 8).toISOString(),
    resolvedAt: null
  },
  {
    id: '4c77f0a9-19be-44cc-a128-338810294851',
    customerName: 'Siddharth Nair',
    amountAtRisk: 3500,
    status: 'FAILED',
    diagnosis: 'EXPIRED_CARD',
    diagnosisConfidence: 0.98,
    selectedStrategy: 'REQUEST_NEW_CARD',
    attemptCount: 3,
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString(),
    resolvedAt: new Date(Date.now() - 3600000 * 18).toISOString()
  }
];

export const mockCaseDetails: Record<string, RecoveryCaseDetail> = {
  '2b9c4ad9-f860-4797-a7c1-a0e919c87874': {
    id: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    paymentId: 'pay_Nzk12049182941',
    customerName: 'Rahul Sharma',
    customerEmail: 'rahul.sharma@example.com',
    customerPastSuccessfulPayments: 14,
    customerPastFailedPayments: 1,
    amountAtRisk: 15000,
    status: 'RECOVERED',
    diagnosis: 'Temporary payment failure (Bank Gateway Timeout)',
    diagnosisConfidence: 0.91,
    recoveryProbability: 0.78,
    expectedRecoveryValue: 11700,
    recoveryCost: 15,
    selectedStrategy: 'WAIT_AND_RETRY',
    attemptCount: 1,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    resolvedAt: new Date(Date.now() - 3600000 * 1.5).toISOString(),
    agentDecisions: [
      {
        id: 'dec_101',
        agentType: 'DETECTION',
        inputSummary: 'Payment pay_Nzk12049182941 failed with code GATEWAY_TIMED_OUT',
        decision: 'PAYMENT_FAILURE_RECOVERABLE',
        confidence: 0.96,
        reasoning: [
          'High customer LTV (14 previous successful transactions)',
          'Failure code GATEWAY_TIMED_OUT indicates temporary infrastructure latency'
        ],
        llmModel: 'Grok-2-Finance (Heuristic Fallback)',
        latencyMs: 140,
        createdAt: new Date(Date.now() - 3600000 * 2).toISOString()
      },
      {
        id: 'dec_102',
        agentType: 'DIAGNOSIS',
        inputSummary: 'Customer bank response code 504 Gateway Timeout',
        decision: 'TEMPORARY_BANK_OUTAGE',
        confidence: 0.91,
        reasoning: [
          'HDFC gateway load spikes reported in last 15 minutes',
          'Card is active and valid until 2028'
        ],
        llmModel: 'Grok-2-Finance (Heuristic Fallback)',
        latencyMs: 185,
        createdAt: new Date(Date.now() - 3600000 * 2 + 1000).toISOString()
      },
      {
        id: 'dec_103',
        agentType: 'STRATEGY',
        inputSummary: 'Diagnosis: TEMPORARY_BANK_OUTAGE, Amount: ₹15,000',
        decision: 'WAIT_AND_RETRY',
        confidence: 0.78,
        reasoning: [
          'Optimal retry window: 15-30 minutes after failure',
          'Expected recovery probability: 78%',
          'Expected net value: ₹11,700'
        ],
        llmModel: 'Grok-2-Finance (Heuristic Fallback)',
        latencyMs: 210,
        createdAt: new Date(Date.now() - 3600000 * 2 + 2000).toISOString()
      },
      {
        id: 'dec_104',
        agentType: 'POLICY_ENGINE',
        inputSummary: 'Proposed Strategy: WAIT_AND_RETRY, Case: 2b9c4ad9',
        decision: 'APPROVED',
        confidence: 1.0,
        reasoning: [
          '✓ Attempt count 1 <= MAX_RECOVERY_ATTEMPTS (3)',
          '✓ Retry interval 25m >= MIN_RETRY_INTERVAL (30m window)',
          '✓ Total recovery budget ₹15 <= MAX_TOTAL_RECOVERY_COST (₹50,000)'
        ],
        llmModel: 'Deterministic Engine v1.0',
        latencyMs: 12,
        createdAt: new Date(Date.now() - 3600000 * 2 + 3000).toISOString()
      }
    ],
    attempts: [
      {
        id: 'att_1',
        attemptNumber: 1,
        strategyUsed: 'WAIT_AND_RETRY',
        status: 'RECOVERED',
        recoveredAmount: 15000,
        razorpayOrderId: 'order_Nzk99102495',
        razorpayPaymentLink: 'https://rzp.io/i/rec_2b9c4ad9',
        createdAt: new Date(Date.now() - 3600000 * 1.5).toISOString()
      }
    ]
  },
  '9a12c4bf-7019-482f-b12a-8819204910ef': {
    id: '9a12c4bf-7019-482f-b12a-8819204910ef',
    paymentId: 'pay_M8192049182049',
    customerName: 'Vikram Malhotra',
    customerEmail: 'vikram.m@example.com',
    customerPastSuccessfulPayments: 2,
    customerPastFailedPayments: 3,
    amountAtRisk: 7825,
    status: 'POLICY_BLOCKED',
    diagnosis: 'INSUFFICIENT_FUNDS',
    diagnosisConfidence: 0.95,
    recoveryProbability: 0.12,
    expectedRecoveryValue: 939,
    recoveryCost: 45,
    selectedStrategy: 'SEND_PAYMENT_LINK',
    attemptCount: 3,
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString(),
    resolvedAt: new Date(Date.now() - 3600000 * 6).toISOString(),
    agentDecisions: [
      {
        id: 'dec_201',
        agentType: 'STRATEGY',
        inputSummary: 'Diagnosis: INSUFFICIENT_FUNDS, Amount: ₹7,825',
        decision: 'SEND_PAYMENT_LINK',
        confidence: 0.45,
        reasoning: [
          'Suggest secondary payment link delivery via SMS/WhatsApp'
        ],
        llmModel: 'Grok-2-Finance (Heuristic Fallback)',
        latencyMs: 190,
        createdAt: new Date(Date.now() - 3600000 * 6 - 2000).toISOString()
      },
      {
        id: 'dec_202',
        agentType: 'POLICY_ENGINE',
        inputSummary: 'Proposed Strategy: SEND_PAYMENT_LINK, Case: 9a12c4bf',
        decision: 'BLOCKED',
        confidence: 1.0,
        reasoning: [
          '✕ MAX_RECOVERY_ATTEMPTS EXCEEDED: Case has already reached 3 of 3 max allowed attempts',
          '✕ POLICY VIOLATION: Further outbound communication or API orders prohibited',
          '✓ Economic Guardrail triggered: Stopped un-permitted execution'
        ],
        llmModel: 'Deterministic Engine v1.0',
        latencyMs: 8,
        createdAt: new Date(Date.now() - 3600000 * 6).toISOString()
      }
    ],
    attempts: [
      {
        id: 'att_201',
        attemptNumber: 1,
        strategyUsed: 'WAIT_AND_RETRY',
        status: 'FAILED',
        recoveredAmount: 0,
        failureReason: 'Bank returned insufficient funds',
        createdAt: new Date(Date.now() - 3600000 * 11).toISOString()
      },
      {
        id: 'att_202',
        attemptNumber: 2,
        strategyUsed: 'SEND_PAYMENT_LINK',
        status: 'FAILED',
        recoveredAmount: 0,
        failureReason: 'Customer link expired after 24h',
        createdAt: new Date(Date.now() - 3600000 * 9).toISOString()
      },
      {
        id: 'att_203',
        attemptNumber: 3,
        strategyUsed: 'SEND_PAYMENT_LINK',
        status: 'POLICY_BLOCKED',
        recoveredAmount: 0,
        failureReason: 'Policy Engine blocked execution: Maximum attempts (3/3) exceeded',
        createdAt: new Date(Date.now() - 3600000 * 6).toISOString()
      }
    ]
  }
};

export const mockPolicyRules: PolicyRule[] = [
  {
    id: 'pol_101',
    name: 'MAX_RECOVERY_ATTEMPTS',
    ruleType: 'INTEGER',
    value: '3',
    enabled: true,
    description: 'Maximum number of recovery attempts permitted per failed payment case.'
  },
  {
    id: 'pol_102',
    name: 'MIN_RETRY_INTERVAL',
    ruleType: 'DURATION',
    value: '30 min',
    enabled: true,
    description: 'Minimum quiet period required before attempting subsequent retries.'
  },
  {
    id: 'pol_103',
    name: 'MAX_DISCOUNT_PERCENTAGE',
    ruleType: 'PERCENTAGE',
    value: '10%',
    enabled: true,
    description: 'Upper threshold on payment discounts offered during recovery.'
  },
  {
    id: 'pol_104',
    name: 'MAX_AUTO_INCENTIVE',
    ruleType: 'CURRENCY',
    value: '₹500',
    enabled: true,
    description: 'Maximum automated incentive credit per customer recovery.'
  },
  {
    id: 'pol_105',
    name: 'NO_CUSTOMER_CONTACT_AFTER',
    ruleType: 'TIME_WINDOW',
    value: '21:00',
    enabled: true,
    description: 'Do not trigger customer notifications or SMS after 9:00 PM local time.'
  },
  {
    id: 'pol_106',
    name: 'STOP_AFTER_CUSTOMER_OPTOUT',
    ruleType: 'BOOLEAN',
    value: 'TRUE',
    enabled: true,
    description: 'Immediately halt recovery pipelines if customer indicates opt-out.'
  },
  {
    id: 'pol_107',
    name: 'MAX_TOTAL_RECOVERY_COST',
    ruleType: 'CURRENCY',
    value: '₹50,000',
    enabled: true,
    description: 'Global daily merchant budget cap for recovery engine operations.'
  }
];

export const mockDecisions: AgentDecision[] = [
  {
    id: 'dec_301',
    agentType: 'POLICY_ENGINE',
    inputSummary: 'Payment ₹25 failure, Recovery probability 12%',
    decision: 'NO_ACTION',
    confidence: 0.99,
    reasoning: ['Economic Guardrail: Operational cost (₹15) exceeds 50% of recoverable value'],
    llmModel: 'Deterministic Engine v1.0',
    latencyMs: 5,
    createdAt: new Date(Date.now() - 60000 * 5).toISOString()
  },
  {
    id: 'dec_302',
    agentType: 'STRATEGY',
    inputSummary: 'Payment ₹15,000 failure, Temporary bank outage detected',
    decision: 'EXECUTED',
    confidence: 0.78,
    reasoning: ['Strategy WAIT_AND_RETRY validated against merchant policies'],
    llmModel: 'Grok-2-Finance (Heuristic Fallback)',
    latencyMs: 165,
    createdAt: new Date(Date.now() - 60000 * 12).toISOString()
  },
  {
    id: 'dec_303',
    agentType: 'POLICY_ENGINE',
    inputSummary: 'Payment ₹7,825 failure, Attempt 3/3 reached',
    decision: 'BLOCKED',
    confidence: 1.0,
    reasoning: ['Maximum recovery attempts (3) exceeded for case 9a12c4bf'],
    llmModel: 'Deterministic Engine v1.0',
    latencyMs: 6,
    createdAt: new Date(Date.now() - 60000 * 25).toISOString()
  },
  {
    id: 'dec_304',
    agentType: 'STRATEGY',
    inputSummary: 'Payment ₹4,500 failure, High value customer',
    decision: 'APPROVAL_REQUIRED',
    confidence: 0.75,
    reasoning: ['Incentive strategy requires manual operational team sign-off'],
    llmModel: 'Grok-2-Finance (Heuristic Fallback)',
    latencyMs: 220,
    createdAt: new Date(Date.now() - 60000 * 45).toISOString()
  }
];

export const mockWebhookEvents: WebhookEvent[] = [
  {
    id: 'evt_fixed_1',
    razorpayEventId: 'event_captured_991204',
    eventType: 'payment.captured',
    signatureValid: true,
    status: 'PROCESSED',
    relatedCaseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    externalRef: 'pay_Nzk12049182941',
    note: 'Razorpay webhook signature verified. Settlement reconciled for ₹15,000.',
    receivedAt: new Date(Date.now() - 3600000 * 1.5).toISOString(),
    processedAt: new Date(Date.now() - 3600000 * 1.5 + 400).toISOString()
  },
  {
    id: 'evt_fixed_2',
    razorpayEventId: 'event_captured_991204',
    eventType: 'payment.captured',
    signatureValid: true,
    status: 'DUPLICATE',
    relatedCaseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    externalRef: 'pay_Nzk12049182941',
    note: 'Replay guard caught duplicate event delivery. Ignored without side-effects.',
    receivedAt: new Date(Date.now() - 3600000 * 1.4).toISOString(),
    processedAt: new Date(Date.now() - 3600000 * 1.4 + 50).toISOString()
  },
  {
    id: 'evt_fixed_3',
    razorpayEventId: 'event_tampered_000192',
    eventType: 'payment.captured',
    signatureValid: false,
    status: 'INVALID_SIGNATURE',
    relatedCaseId: null,
    externalRef: 'pay_UNTRUSTED_991',
    note: 'HMAC-SHA256 signature verification failed. Delivery rejected with HTTP 400.',
    receivedAt: new Date(Date.now() - 3600000 * 3.2).toISOString(),
    processedAt: new Date(Date.now() - 3600000 * 3.2 + 20).toISOString()
  }
];

export const mockHealthResponse: HealthResponse = {
  status: 'UP',
  service: 'recoverai-backend',
  timestamp: new Date().toISOString(),
  timezone: 'Asia/Kolkata',
  config: {
    webhookSecretConfigured: true,
    razorpayKeysConfigured: true,
    grokApiKeyConfigured: false
  },
  mode: 'DEGRADED_FALLBACK'
};

export const mockLiveActivityEvents: LiveActivityEvent[] = [
  {
    id: 'live_1',
    timestamp: '10:42:01',
    stage: 'PAYMENT_FAILED',
    title: 'PAYMENT FAILED',
    subtitle: '₹15,000 payment failed (Razorpay pay_Nzk12049182941)',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    amount: 15000,
    metadata: {
      failureCode: 'GATEWAY_TIMED_OUT',
      customer: 'Rahul Sharma'
    }
  },
  {
    id: 'live_2',
    timestamp: '10:42:01',
    stage: 'DIAGNOSING',
    title: 'DIAGNOSING',
    subtitle: 'Temporary failure detected',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    metadata: {
      confidence: '91%',
      bankCode: 'HDFC_504'
    }
  },
  {
    id: 'live_3',
    timestamp: '10:42:02',
    stage: 'AI_DECISION',
    title: 'RECOVERY STRATEGY DECISION',
    subtitle: 'Strategy: WAIT_AND_RETRY',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    metadata: {
      recoveryProbability: '78%',
      expectedValue: '₹11,700'
    }
  },
  {
    id: 'live_4',
    timestamp: '10:42:02',
    stage: 'POLICY_CHECK',
    title: 'POLICY CHECK',
    subtitle: '✓ Allowed by Merchant Guardrails',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    metadata: {
      maxAttempts: '3',
      currentAttempt: '1',
      costBudget: '₹15 / ₹50,000'
    }
  },
  {
    id: 'live_5',
    timestamp: '10:42:03',
    stage: 'EXECUTED',
    title: 'EXECUTED',
    subtitle: 'Razorpay order & retry attempt scheduled',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    metadata: {
      razorpayOrder: 'order_Nzk99102495',
      status: 'PENDING'
    }
  },
  {
    id: 'live_6',
    timestamp: '10:43:17',
    stage: 'WEBHOOK_RECEIVED',
    title: 'WEBHOOK RECEIVED',
    subtitle: 'payment.captured signature VALID',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    metadata: {
      eventId: 'event_captured_991204',
      status: 'PROCESSED'
    }
  },
  {
    id: 'live_7',
    timestamp: '10:43:17',
    stage: 'RECOVERED',
    title: 'RECOVERED',
    subtitle: '₹15,000 successfully recovered',
    caseId: '2b9c4ad9-f860-4797-a7c1-a0e919c87874',
    amount: 15000
  },
  {
    id: 'live_blocked_1',
    timestamp: '10:45:00',
    stage: 'POLICY_BLOCKED',
    title: 'POLICY BLOCKED',
    subtitle: 'SEND_PAYMENT_LINK Halted by Guardrails',
    caseId: '9a12c4bf-7019-482f-b12a-8819204910ef',
    blockedInfo: {
      proposedAction: 'SEND_PAYMENT_LINK',
      reason: 'Maximum recovery attempts exceeded',
      attempts: '3 / 3',
      aiDecision: 'BLOCKED',
      razorpayApi: 'NOT CALLED'
    }
  }
];
