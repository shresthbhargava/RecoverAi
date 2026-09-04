-- Phase 4: Razorpay webhook ingestion.
--
-- Two problems have to be solved before a webhook is useful:
--   1. Correlation — an inbound event carries Razorpay's own ids (order_xxx / plink_xxx),
--      not our UUIDs. Until now those ids only ever landed inside recovery_attempt.result
--      as free text, which is unqueryable. external_ref fixes that.
--   2. Idempotency — Razorpay retries a webhook until it gets a 2xx, so the same event
--      WILL arrive more than once. razorpay_event_id is UNIQUE so a replay can never
--      double-count recovered revenue.

ALTER TABLE recovery_attempt
    ADD COLUMN external_ref VARCHAR(64);

COMMENT ON COLUMN recovery_attempt.external_ref IS
    'Razorpay order_id or payment_link id created by this attempt; the join key for inbound webhooks';

CREATE INDEX idx_attempt_external_ref ON recovery_attempt(external_ref);

-- payment.razorpay_payment_id existed in V1 but was never populated. The webhook
-- handler backfills it from payload.payment.entity.id, so it needs an index now.
CREATE INDEX idx_payment_razorpay_id ON payment(razorpay_payment_id);

CREATE TABLE webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Razorpay's x-razorpay-event-id header. UNIQUE is the whole idempotency mechanism:
    -- a duplicate delivery hits this constraint and is short-circuited before any
    -- case state is touched.
    razorpay_event_id VARCHAR(128) NOT NULL UNIQUE,

    event_type VARCHAR(64) NOT NULL,
    signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,

    -- Nullable on purpose: an event can be validly signed and still match no case
    -- of ours (someone else's payment in the same Test Mode account).
    related_case_id UUID REFERENCES recovery_case(id),

    external_ref VARCHAR(64),
    payload JSONB,
    note TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_webhook_received ON webhook_event(received_at DESC);
CREATE INDEX idx_webhook_status ON webhook_event(status);
CREATE INDEX idx_webhook_case ON webhook_event(related_case_id);
