# Phase 4 — Razorpay webhooks

What this phase adds: the system stops trusting its own simulated outcome and starts
reconciling against what actually happened at Razorpay. A recovery attempt that the batch
run recorded as FAILED will flip to SUCCEEDED, and its case to RECOVERED, the moment a
signed `payment.captured` arrives for the order that attempt created.

Everything below assumes the backend runs on `localhost:8080` and Postgres is up.

## The pieces

| File | Role |
| --- | --- |
| `razorpay/RazorpaySignatureVerifier` | HMAC-SHA256 over the raw body, constant-time compare |
| `controller/WebhookController` | `POST /api/webhooks/razorpay`, plus `/recent` and `/stats` |
| `service/WebhookService` | Verify, claim, dispatch, record. Not transactional, on purpose |
| `service/WebhookEventRecorder` | `webhook_event` rows, each in its own REQUIRES_NEW transaction |
| `service/WebhookReconciler` | The only class that touches money. One transaction, all or nothing |
| `entity/WebhookEvent` | One row per delivery, including rejected ones |
| `V2__webhook_support.sql` | `webhook_event` table, `recovery_attempt.external_ref`, indexes |

Three beans instead of one is not decoration. Three different durability rules collide in
this flow and no single transaction satisfies all of them: the event-id claim has to commit
*before* any work starts or concurrent retries race each other; the case update has to be
all-or-nothing; and the audit row has to survive even when the case update rolls back.

## Setup

### 1. Set the webhook secret

Pick any string — this is your secret, not something Razorpay issues. You will type the
same value into the Razorpay dashboard in step 3.

```powershell
$env:RAZORPAY_WEBHOOK_SECRET = "whsec_recoverai_demo_2026"
```

If this is unset, every delivery is rejected as `INVALID_SIGNATURE`. That is deliberate —
an unconfigured box should refuse webhooks rather than accept unverified ones.

### 2. Expose localhost

Razorpay has to reach your machine, so you need a public URL.

```powershell
ngrok http 8080
```

Copy the `https://` forwarding URL. The free tier issues a new one every restart, and
changing it means editing the dashboard again, so start ngrok before you configure the
webhook and leave it running for the whole demo.

### 3. Configure the webhook in Razorpay

Dashboard → Settings → Webhooks → Add New Webhook.

- **URL**: `https://<your-ngrok-subdomain>.ngrok-free.app/api/webhooks/razorpay`
- **Secret**: the exact value from step 1
- **Active events**: `payment.captured`, `payment.failed`, `payment_link.paid`, `order.paid`

Do not subscribe to `payment.authorized`. Orders are created with `payment_capture=1`, so
an authorization is always followed by a capture, and acting on the earlier event would
count revenue before it settles. The handler ignores it if it arrives anyway.

## Demo without waiting for a real payment

`scripts/send-test-webhook.ps1` forges a correctly signed delivery. First get a real ref
out of your own data, because an event that matches nothing is correctly reported as
`IGNORED`:

```sql
select external_ref, action, status
from recovery_attempt
where external_ref is not null
order by executed_at desc
limit 5;
```

Then, from `backend/`:

```powershell
# 1. The happy path — should return PROCESSED and flip the case to RECOVERED
.\scripts\send-test-webhook.ps1 -OrderId order_ABC123 -Secret $env:RAZORPAY_WEBHOOK_SECRET -Amount 2500

# 2. Idempotency — same event id twice. Second returns DUPLICATE, revenue counted once
.\scripts\send-test-webhook.ps1 -OrderId order_ABC123 -Secret $env:RAZORPAY_WEBHOOK_SECRET -EventId evt_fixed_1
.\scripts\send-test-webhook.ps1 -OrderId order_ABC123 -Secret $env:RAZORPAY_WEBHOOK_SECRET -EventId evt_fixed_1

# 3. Signature verification — corrupted HMAC. Expect HTTP 400 and an INVALID_SIGNATURE row
.\scripts\send-test-webhook.ps1 -OrderId order_ABC123 -Secret $env:RAZORPAY_WEBHOOK_SECRET -Tamper

# 4. Payment link variant
.\scripts\send-test-webhook.ps1 -PaymentLinkId plink_XYZ789 -Secret $env:RAZORPAY_WEBHOOK_SECRET -Event payment_link.paid
```

Then show the evidence:

```bash
curl http://localhost:8080/api/webhooks/recent
curl http://localhost:8080/api/webhooks/stats
```

The rejected and duplicate rows are the point. A table containing only successes proves
nothing; a table that also shows `INVALID_SIGNATURE` and `DUPLICATE` rows is the evidence
that verification and the idempotency guard actually fire.

Screen 2's SSE feed also carries three new stages — `WEBHOOK_RECEIVED`,
`WEBHOOK_RECONCILED`, `WEBHOOK_REJECTED` — so a callback appears live while you talk.

## Response codes and why

| Outcome | HTTP | Reasoning |
| --- | --- | --- |
| `PROCESSED` | 200 | Reconciled |
| `DUPLICATE` | 200 | Replay suppressed. Razorpay must stop retrying |
| `IGNORED` | 200 | Signed but not ours, or an event type we don't handle |
| `INVALID_SIGNATURE` | 400 | We do not want this delivery back |
| `FAILED` | 200 | See below |

Returning 200 on `FAILED` is a trade-off, not an oversight. The event id is already claimed
by the time reconciliation runs, so a Razorpay retry would hit the UNIQUE constraint and be
classified as a replay rather than genuinely retried. Since automatic retry cannot help,
the honest behaviour is to acknowledge the delivery and leave a `FAILED` row visible at
`/api/webhooks/recent` for manual replay, rather than have Razorpay hammer an endpoint that
will keep short-circuiting.

## Correlation

An inbound event carries Razorpay's ids (`order_xxx`, `plink_xxx`), never our UUIDs.
`recovery_attempt.external_ref` is the only route back to a `RecoveryCase`, and
`RecoveryExecutor` populates it for the two real-API actions.

Razorpay puts the useful id in a different place per event type, so rather than trusting one
JSON path the handler collects every plausible ref and tries each in order:

1. `payload.payment_link.entity.id` — first, because a payment link's internal order id is
   one we never saw and would match nothing
2. `payload.payment.entity.order_id`
3. `payload.order.entity.id`

The lookup is newest-first, because `external_ref` is indexed but not unique: a retried case
can legitimately produce several orders, and a late webhook for an old order must not
overwrite a newer attempt's result.

## What reconciliation deliberately does not do

A webhook records facts. It does not make decisions — the same rule `RecoveryExecutor`
follows.

So a confirmed capture *does* resolve a case as `RECOVERED`, but a reported failure does
**not** mark a case `UNRECOVERABLE`. Concluding a case is finished belongs to the agents and
the Policy Engine on the next run. Letting an inbound HTTP request short-circuit that would
put decision-making back in exactly the place the architecture keeps it out of.

Two other things it leaves alone: `attemptCount` and `recoveryCost`. `finalizeCase` already
incremented the count and charged the cost when the API call was made. This is the same
attempt being confirmed, not a new one.

## Troubleshooting

**Every delivery comes back `INVALID_SIGNATURE`.** Almost always the secret differs between
`RAZORPAY_WEBHOOK_SECRET` and the dashboard. If they match, something is re-serialising the
body — the controller must take `@RequestBody String`, never a mapped DTO, because Jackson
round-tripping reorders keys and normalises whitespace, and the recomputed HMAC will not
match. If you wrote your own test client, sign and send the identical byte array.

**Everything comes back `IGNORED` with "No recovery attempt matches".** `external_ref` is
null on your attempts. Check with the SQL above. Only `RETRY_PAYMENT`, `WAIT_AND_RETRY` and
`SEND_PAYMENT_LINK` make real API calls and therefore have a ref; reminders and incentives
are simulated and correctly have none.

**`UnexpectedRollbackException` in the logs.** Something is catching a
`DataIntegrityViolationException` inside the transaction that caused it. Hibernate marks the
transaction rollback-only at flush, so returning normally makes the commit fail instead. The
catch has to sit outside the transactional boundary — that is why `WebhookEventRecorder`
lets the violation escape and `WebhookService` handles it.

**Flyway complains about `V2__webhook_support.sql`.** If you had already started the app
against this database before V2 existed, Flyway will apply it on next boot. If you edited V2
after it was applied, the checksum no longer matches — drop and recreate the database, since
this is demo data.

**Nothing arrives from the dashboard at all.** Check the ngrok request log first. Razorpay's
dashboard has its own delivery log per webhook with the response it got, which distinguishes
"never left Razorpay" from "your app returned 400".
