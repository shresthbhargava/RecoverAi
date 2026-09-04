export interface AnalyticsSummary {
  totalEventsProcessed: number;
  revenueAtRisk: number;
  recoverableCases: number;
  recoveryAttempts: number;
  successfulRecoveries: number;
  failedRecoveries: number;
  blockedActions: number;
  revenueRecovered: number;
  recoveryRate: number;
  recoveryCost: number;
  netRecoveredRevenue: number;
}

export type CaseStatus =
  | 'DETECTED'
  | 'DIAGNOSED'
  | 'STRATEGY_SELECTED'
  | 'POLICY_CHECKED'
  | 'EXECUTING'
  | 'AWAITING_APPROVAL'
  | 'RECOVERED'
  | 'FAILED'
  | 'POLICY_BLOCKED';

export interface RecoveryCaseSummary {
  id: string;
  customerName: string;
  amountAtRisk: number;
  status: CaseStatus;
  diagnosis: string;
  diagnosisConfidence: number;
  selectedStrategy: string;
  attemptCount: number;
  createdAt: string;
  resolvedAt?: string | null;
}

export interface AgentDecision {
  id: string;
  agentType: 'DETECTION' | 'DIAGNOSIS' | 'STRATEGY' | 'POLICY_ENGINE' | 'EXECUTOR';
  inputSummary: string;
  decision: string;
  confidence: number;
  reasoning: string[];
  llmModel: string;
  latencyMs: number;
  createdAt: string;
}

export interface RecoveryAttempt {
  id: string;
  attemptNumber: number;
  strategyUsed: string;
  status: 'PENDING' | 'EXECUTED' | 'FAILED' | 'RECOVERED' | 'POLICY_BLOCKED';
  recoveredAmount: number;
  razorpayOrderId?: string;
  razorpayPaymentLink?: string;
  failureReason?: string;
  createdAt: string;
}

export interface RecoveryCaseDetail {
  id: string;
  paymentId: string;
  customerName: string;
  customerEmail: string;
  customerPastSuccessfulPayments: number;
  customerPastFailedPayments: number;
  amountAtRisk: number;
  status: CaseStatus;
  diagnosis: string;
  diagnosisConfidence: number;
  recoveryProbability: number;
  expectedRecoveryValue: number;
  recoveryCost: number;
  selectedStrategy: string;
  attemptCount: number;
  createdAt: string;
  resolvedAt?: string | null;
  agentDecisions: AgentDecision[];
  attempts: RecoveryAttempt[];
}

export interface PolicyRule {
  id: string;
  name: string;
  ruleType: string;
  value: string;
  enabled: boolean;
  description: string;
}

export type WebhookStatus = 'PROCESSED' | 'DUPLICATE' | 'INVALID_SIGNATURE' | 'FAILED' | 'IGNORED';

export interface WebhookEvent {
  id: string;
  razorpayEventId: string;
  eventType: string;
  signatureValid: boolean;
  status: WebhookStatus;
  relatedCaseId?: string | null;
  externalRef?: string;
  note?: string;
  receivedAt: string;
  processedAt?: string;
}

export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
  timezone: string;
  config: {
    webhookSecretConfigured: boolean;
    razorpayKeysConfigured: boolean;
    grokApiKeyConfigured: boolean;
  };
  mode: 'FULL' | 'DEGRADED_FALLBACK';
}

export interface LiveActivityEvent {
  id: string;
  timestamp: string;
  stage: 'PAYMENT_FAILED' | 'DIAGNOSING' | 'AI_DECISION' | 'POLICY_CHECK' | 'EXECUTED' | 'WEBHOOK_RECEIVED' | 'RECOVERED' | 'POLICY_BLOCKED';
  title: string;
  subtitle: string;
  caseId: string;
  amount?: number;
  metadata?: Record<string, string | number | boolean>;
  blockedInfo?: {
    proposedAction: string;
    reason: string;
    attempts: string;
    aiDecision: string;
    razorpayApi: string;
  };
}
