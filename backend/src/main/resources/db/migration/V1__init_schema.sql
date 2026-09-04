CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(20),
    total_successful_payments INT NOT NULL DEFAULT 0,
    total_failed_payments INT NOT NULL DEFAULT 0,
    lifetime_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    opted_out BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    razorpay_payment_id VARCHAR(64),
    customer_id UUID NOT NULL REFERENCES customer(id),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    failure_reason VARCHAR(64),
    payment_method VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_customer ON payment(customer_id);

CREATE TABLE recovery_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payment(id),
    customer_id UUID NOT NULL REFERENCES customer(id),
    amount_at_risk NUMERIC(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    diagnosis VARCHAR(64),
    diagnosis_confidence NUMERIC(4,3),
    recovery_probability NUMERIC(4,3),
    expected_recovery_value NUMERIC(12,2),
    recovery_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
    selected_strategy VARCHAR(32),
    attempt_count INT NOT NULL DEFAULT 0,
    last_contact_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP
);
CREATE INDEX idx_case_status ON recovery_case(status);
CREATE INDEX idx_case_customer ON recovery_case(customer_id);

CREATE TABLE recovery_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recovery_case_id UUID NOT NULL REFERENCES recovery_case(id),
    attempt_number INT NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_real_api_action BOOLEAN NOT NULL DEFAULT FALSE,
    executed_at TIMESTAMP NOT NULL DEFAULT now(),
    result VARCHAR(255),
    amount_recovered NUMERIC(12,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_attempt_case ON recovery_attempt(recovery_case_id);

CREATE TABLE policy_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(64) NOT NULL UNIQUE,
    rule_type VARCHAR(32) NOT NULL,
    value VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT
);

CREATE TABLE agent_decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recovery_case_id UUID NOT NULL REFERENCES recovery_case(id),
    agent_type VARCHAR(32) NOT NULL,
    input_summary TEXT,
    decision VARCHAR(64),
    confidence NUMERIC(4,3),
    reasoning JSONB,
    llm_model VARCHAR(64),
    latency_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_decision_case ON agent_decision(recovery_case_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recovery_case_id UUID REFERENCES recovery_case(id),
    timestamp TIMESTAMP NOT NULL DEFAULT now(),
    actor VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    previous_state VARCHAR(64),
    new_state VARCHAR(64),
    reason TEXT,
    metadata JSONB
);
CREATE INDEX idx_audit_case ON audit_log(recovery_case_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);

-- Seed default policy rules (values mirror application.yml defaults; editable at runtime via /api/policies)
INSERT INTO policy_rule (name, rule_type, value, enabled, description) VALUES
('MAX_RECOVERY_ATTEMPTS', 'INTEGER', '3', TRUE, 'Maximum number of recovery attempts per case before it is marked unrecoverable/escalated'),
('MIN_RETRY_INTERVAL_MINUTES', 'INTEGER', '30', TRUE, 'Minimum minutes between consecutive recovery attempts on the same case'),
('MAX_DISCOUNT_PERCENTAGE', 'PERCENTAGE', '10', TRUE, 'Maximum discount percentage the AI may offer without merchant approval'),
('MAX_AUTO_INCENTIVE', 'AMOUNT', '500', TRUE, 'Maximum incentive amount (INR) the AI may authorize automatically; above this requires merchant approval'),
('NO_CUSTOMER_CONTACT_AFTER', 'HOUR_OF_DAY', '21', TRUE, 'No outbound customer contact after this hour (24h, local time)'),
('STOP_AFTER_CUSTOMER_OPTOUT', 'BOOLEAN', 'true', TRUE, 'Never contact a customer who has opted out'),
('MAX_TOTAL_RECOVERY_COST', 'AMOUNT', '50000', TRUE, 'Maximum cumulative recovery cost (INR) allowed across a batch run');
