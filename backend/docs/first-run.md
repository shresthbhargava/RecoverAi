# First run — from nothing to a reconciled webhook

Written for the case you are actually in: **no Razorpay keys, no Grok key, nothing compiled yet.**
Everything here works without a single external account.

At the end you will have seen the agent pipeline diagnose six failed payments, the Policy
Engine veto an action, and a signed webhook flip a failed recovery into a recovered one.

Run everything from `razororPay\backend` unless a step says otherwise.

---

## Step 1 — find out what you actually have

```powershell
cd C:\Users\Shresth\OneDrive\Documents\razororPay\backend
.\scripts\check-env.ps1
```

If PowerShell refuses to run it ("running scripts is disabled on this system"), unblock scripts
for this session only — this does not change your machine's permanent policy:

```powershell
Set-ExecutionPolicy -Scope Process -Bypass
```

The script only reads. It prints a line per check and finishes with a numbered list of anything
blocking. Work through that list, then re-run it until the verdict is clean. Everything below
assumes it is.

If your Postgres password is not `postgres`, tell the script:

```powershell
.\scripts\check-env.ps1 -DbPassword yourrealpassword
```

---

## Step 2 — make the database exist

Only if step 1 said the database was missing. Flyway creates the *tables*, but it cannot create
the *database* — that has to exist first.

```powershell
psql -U postgres -c "CREATE DATABASE recoverai;"
```

If `psql` is not on your PATH, use the full path the check script printed, or open pgAdmin and
right-click Databases -> Create -> Database, name it `recoverai`.

Verify:

```powershell
psql -U postgres -lqt
```

You should see `recoverai` in the list. Do not create any tables by hand — Flyway owns them, and
`ddl-auto: validate` means the app will refuse to start if the schema does not match the entities.

---

## Step 3 — set the webhook secret

This is a value **you invent**. It is not issued by Razorpay and it is not a credential from
anywhere; it is just a shared string used to sign local test deliveries. Anything works:

```
whsec_recoverai_demo_2026
```

Where you set it depends on how you start the app, and this trips people up constantly:

**If you run from IntelliJ** (the green arrow on `RecoveraiApplication`), a `$env:` variable set in
a PowerShell window is invisible to it. Set it in the run configuration instead:
Run -> Edit Configurations -> select `RecoveraiApplication` -> Environment variables -> paste

```
RAZORPAY_WEBHOOK_SECRET=whsec_recoverai_demo_2026
```

**If you run from a terminal**, set it in that same terminal before starting:

```powershell
$env:RAZORPAY_WEBHOOK_SECRET = "whsec_recoverai_demo_2026"
```

Skipping this is not fatal — the app starts fine. But every webhook comes back
`INVALID_SIGNATURE`, because an unconfigured secret means no delivery can be trusted, which is
deliberate. You would get to step 8 and think the signature check was broken.

**Environment variables are read once, at startup.** Setting one after the app is already running
does nothing until you restart it. After restarting, confirm the process actually received it:

```powershell
curl.exe http://localhost:8080/api/health
```

Look for `"webhookSecretConfigured": true`. If it says `false`, the variable did not reach the JVM
and step 8 will fail no matter what you pass to the signing script.

---

## Step 4 — compile it for the first time

There is **no Maven wrapper** in this repo (no `mvnw.cmd`), so use IntelliJ's bundled Maven rather
than installing Maven separately.

In IntelliJ: open the Maven tool window (right edge, or View -> Tool Windows -> Maven) ->
expand `recoverai-backend` -> Lifecycle -> double-click **compile**.

This is the first real compile this project has ever had, so treat the output as information rather
than a formality. Expect it to take a minute the first time while Maven downloads Spring Boot.

If it fails, the errors you are most likely to see:

*Hundreds of "cannot find symbol: method getId()" / "builder()"* — Lombok is not generating code.
Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors ->
tick **Enable annotation processing**.

*"invalid target release: 21"* — the project SDK is not 21. File -> Project Structure -> Project ->
set SDK and Language level to 21.

Anything else, paste it to me verbatim rather than trying to fix it blind.

---

## Step 5 — start it and watch Flyway

Run `RecoveraiApplication`. In the log, look for Flyway applying **both** migrations:

```
Migrating schema "public" to version "1 - init schema"
Migrating schema "public" to version "2 - webhook support"
Successfully applied 2 migrations
```

Then confirm it is listening:

```powershell
curl http://localhost:8080/api/health
```

If startup fails with `Schema-validation: missing table` or `missing column`, the entities and the
migrations disagree — send me the exact line, because that is a code bug, not a setup mistake.

If it fails with `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`, see the
timezone entry in the troubleshooting section at the bottom. Short version: your app compiled and
your credentials are fine, and `RecoveraiApplication` already contains the fix — you need a rebuild.

---

## Step 6 — run the agent pipeline

```powershell
curl -X POST http://localhost:8080/api/batch/process ^
  -H "Content-Type: application/json" ^
  -d "@scripts/demo-batch.json"
```

(In PowerShell, `^` is not a line-continuation character — either put that command on one line, or
use a backtick `` ` `` at the end of each line instead.)

Six events go in. Because there is no Grok key, both LLM agents fail their API call and fall back
to rule-based heuristics — that is the `wasFallback: true` flag, and it is honest behaviour, not a
bug. The fallback rules are deterministic, so the outcome is exactly predictable:

| # | amount | diagnosis | confidence | action chosen | why |
| --- | --- | --- | --- | --- | --- |
| 1 | 4500 | TEMPORARY_BANK_FAILURE | 0.6 | WAIT_AND_RETRY | reason contains "BANK"/"TIMEOUT" |
| 2 | 12000 | USER_ABANDONMENT | 0.5 | SEND_PAYMENT_LINK | event is CHECKOUT_ABANDONED |
| 3 | 2500 | INSUFFICIENT_FUNDS | 0.6 | NO_ACTION | no heuristic action for this diagnosis |
| 4 | 800 | UNKNOWN | 0.3 | NO_ACTION | confidence below 0.5 |
| 5 | 25 | USER_ABANDONMENT | 0.5 | NO_ACTION | **economic guardrail** |
| 6 | 15000 | TEMPORARY_BANK_FAILURE | 0.6 | WAIT_AND_RETRY | reason contains "TIMEOUT" |

Event 5 is the one worth pointing at in a demo. The Strategy Agent proposed `SEND_PAYMENT_LINK`,
then `buildOutcome` computed expected net recovery as `25 x 0.35 - 10 = -1.25` and overrode its own
recommendation to `NO_ACTION`. The system refused to spend Rs 10 chasing Rs 8.75. Check it:

```powershell
curl http://localhost:8080/api/agent-decisions/recent
```

and look for the reasoning string `Overridden by economic guardrail`.

### Expect zero recoveries here, and do not panic

The response will read roughly:

```json
{ "transactionsAnalyzed": 6, "successfulRecoveries": 0, "revenueRecovered": 0, ... }
```

That is correct. Events 1, 2 and 6 chose real-API actions, so `RecoveryExecutor` tried to create a
genuine Razorpay order. With no keys, Razorpay answers 401, the call throws, and the attempt is
recorded as FAILED with `is_real_api_action = true`. Nothing is faked to look successful — that is
one of the graded requirements.

Steps 7 and 8 are what produce your first RECOVERED case.

### One time-of-day trap

`SEND_PAYMENT_LINK` is a customer-facing action, and `NO_CUSTOMER_CONTACT_AFTER` is 21. If you run
this **after 9pm local time**, event 2 comes back `BLOCKED` instead, with reason
"Outside allowed contact window". That is the Policy Engine working correctly, but it will confuse
you if you are not expecting it.

---

## Step 7 — give the webhook something to match

A webhook arrives carrying Razorpay's ids (`order_xxx`), never our UUIDs. The only route back to a
case is `recovery_attempt.external_ref`, and that column is only populated when a real Razorpay API
call succeeded. Yours all failed, so:

```powershell
psql -U postgres -d recoverai -c "select external_ref, action, status from recovery_attempt where external_ref is not null;"
```

returns nothing. Plant one by hand. This is the only step that exists purely because you have no
Razorpay keys — with keys, step 6 fills this in for you.

```sql
UPDATE recovery_attempt
SET external_ref = 'order_DEMO0001'
WHERE id = (
    SELECT ra.id
      FROM recovery_attempt ra
      JOIN recovery_case rc ON rc.id = ra.recovery_case_id
     WHERE ra.is_real_api_action = TRUE
       AND ra.status = 'FAILED'
       AND ra.external_ref IS NULL
     ORDER BY rc.amount_at_risk DESC
     LIMIT 1
)
RETURNING external_ref, recovery_case_id;
```

That picks the highest-value failed real-API attempt, which will be the Rs 15,000 case. Now record
what the "before" state looks like, so you can prove the webhook changed it:

```sql
SELECT rc.id AS case_id, rc.status AS case_status, rc.amount_at_risk,
       ra.status AS attempt_status, ra.amount_recovered, ra.external_ref,
       p.status AS payment_status, p.razorpay_payment_id
  FROM recovery_case rc
  JOIN recovery_attempt ra ON ra.recovery_case_id = rc.id
  JOIN payment p ON p.id = rc.payment_id
 WHERE ra.external_ref = 'order_DEMO0001';
```

Expect: case `IN_PROGRESS`, attempt `FAILED`, `amount_recovered` 0.00, payment `FAILED`,
`razorpay_payment_id` null.

---

## Step 8 — fire a signed webhook

```powershell
.\scripts\send-test-webhook.ps1 -OrderId order_DEMO0001 -Secret $env:RAZORPAY_WEBHOOK_SECRET -Amount 15000
```

If you set the secret only in IntelliJ's run configuration, `$env:RAZORPAY_WEBHOOK_SECRET` is empty
in this terminal — pass the literal value instead:

```powershell
.\scripts\send-test-webhook.ps1 -OrderId order_DEMO0001 -Secret whsec_recoverai_demo_2026 -Amount 15000
```

Expected response:

```json
{ "status": "PROCESSED", "message": "Case marked RECOVERED, 15000.00 confirmed (overrode simulated failure)", "caseId": "..." }
```

Now re-run the "before" query from step 7. Every one of these should have changed:

| column | before | after |
| --- | --- | --- |
| `recovery_case.status` | IN_PROGRESS | **RECOVERED** |
| `recovery_case.resolved_at` | null | a timestamp |
| `recovery_attempt.status` | FAILED | **SUCCEEDED** |
| `recovery_attempt.amount_recovered` | 0.00 | **15000.00** |
| `payment.status` | FAILED | SUCCESS |
| `payment.razorpay_payment_id` | null | `pay_test...` |

`recovery_case.attempt_count` and `recovery_cost` should be **unchanged** — this is the same attempt
being confirmed, not a new one, and double-charging the cost would quietly corrupt the net-recovery
number the dashboard reports.

The customer's tally also moves rather than grows: one failed payment becomes one successful
payment, instead of the customer ending up with both.

---

## Step 9 — prove the guards actually fire

A delivery log showing only successes proves nothing. These two are the evidence.

**Replay suppression.** Send the same event id twice:

```powershell
.\scripts\send-test-webhook.ps1 -OrderId order_DEMO0001 -Secret whsec_recoverai_demo_2026 -EventId evt_fixed_1
.\scripts\send-test-webhook.ps1 -OrderId order_DEMO0001 -Secret whsec_recoverai_demo_2026 -EventId evt_fixed_1
```

The second returns `DUPLICATE`. Confirm `amount_recovered` is still 15000.00 and not 30000.00.
(The first of these two may itself return `DUPLICATE` rather than `PROCESSED`, because step 8
already marked the attempt SUCCEEDED — that is the second, independent guard inside the reconciler.)

**Signature verification.** `-Tamper` flips one character of the HMAC:

```powershell
.\scripts\send-test-webhook.ps1 -OrderId order_DEMO0001 -Secret whsec_recoverai_demo_2026 -Tamper
```

Expect **HTTP 400** and `INVALID_SIGNATURE`. Then show the log:

```powershell
curl http://localhost:8080/api/webhooks/recent
curl http://localhost:8080/api/webhooks/stats
```

The rejected and duplicate rows are the point. `/recent` deliberately omits the raw payload,
because a real webhook body carries the customer's email and phone number.

---

## Step 10 — prove the Policy Engine can veto the AI

This is named in the spec, so it is worth being able to show on demand. Without a Grok key the
agents never propose an escalation or an incentive, so the easiest deterministic veto is to tighten
a rule at runtime.

```powershell
curl http://localhost:8080/api/policies
```

Find the `id` of `MAX_RECOVERY_ATTEMPTS`, then set it to 0:

```powershell
curl -X PUT http://localhost:8080/api/policies/PASTE_THE_ID_HERE -H "Content-Type: application/json" -d "{\"value\":\"0\",\"enabled\":true}"
```

Re-run step 6. Every proposed action now comes back `BLOCKED` with
"Maximum recovery attempts (0) exceeded", the cases land in `BLOCKED`, and **no Razorpay call is
made at all** — the veto happens before `RecoveryExecutor` is reached. That is the
"AI proposes, policy disposes" requirement, demonstrated rather than asserted.

Set it back to 3 afterwards.

---

## Two inconsistencies you should know about before a judge finds them

**The detection category is computed twice, by two different rules.** `DetectionAgent.categorize`
knows about `CHECKOUT_ABANDONMENT`, `LINK_EXPIRY`, `SUBSCRIPTION_FAILURE` and
`PAYMENT_METHOD_FAILURE`. `CaseContextBuilder.categoryFromPayment` knows none of those and returns
`UNCATEGORIZED_FAILURE` instead. The first one is what gets written to the audit trail and the live
feed; the second is what actually drives the diagnosis. For 3 of the 6 demo events they disagree —
event 4, for instance, is filed as `PAYMENT_METHOD_FAILURE` in the audit log while the Diagnosis
Agent was handed `UNCATEGORIZED_FAILURE` and concluded `UNKNOWN`. Not wrong output, but two sources
of truth for one fact.

**A blocked case still counts as a failed payment against the customer.**
`recordPaymentOutcome(customer, false)` runs for every attempt that is not SUCCEEDED, including
`BLOCKED` and `PENDING`. So a case the Policy Engine stopped, or one parked awaiting merchant
approval, worsens the customer's failure history even though nothing was ever attempted.

---

## If something goes wrong

**`FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"` at startup.** Read this as good
news first: the failure happens in `QueryExecutorImpl.readStartupMessages`, which runs *after*
authentication, so the code compiled, Postgres is up, your password is right and the `recoverai`
database exists. Only the zone name is wrong.

On Windows, Java maps "India Standard Time" to the deprecated IANA alias `Asia/Calcutta`, and the
JDBC driver sends that as a connection startup parameter. If the server's tzdata omits the
backward-compatibility aliases it rejects the name and drops the connection before Flyway runs.
Confirm with:

```powershell
psql -U postgres -d postgres -c "select name from pg_timezone_names where name in ('Asia/Calcutta','Asia/Kolkata');"
```

Only `Asia/Kolkata` coming back is the confirmation. `RecoveraiApplication` has a static block that
pins the JVM default to `Asia/Kolkata` for exactly this reason, so if you are still seeing the error,
the block is missing or the module was not rebuilt — run Maven `compile` again, or Build -> Rebuild
Project.

Do not work around this by setting `hibernate.jdbc.time_zone` to UTC. Every column is `TIMESTAMP`
without a zone and every entity field is an `Instant`, so that shifts stored timestamps by 5.5 hours,
and `PolicyEngine` reads the local hour-of-day for the no-contact-after-21:00 rule, so it misfires
too. `Asia/Kolkata` is the identical zone under its modern name; nothing else changes.

**Every webhook is `INVALID_SIGNATURE`.** Read the `note` field on the rejection row — it tells you
which of two different problems you have, and they have different fixes:

*`"razorpay.webhook-secret is not configured, so no delivery can be trusted"`* — the app has no secret
at all, so it rejects before comparing anything. What you passed to `-Secret` is irrelevant; that only
signs the outgoing request. Go back to step 3, set the variable, and **restart the app** — environment
variables are read once at startup. Confirm with `curl.exe http://localhost:8080/api/health` and look
for `"webhookSecretConfigured": true`.

*`"X-Razorpay-Signature did not match the HMAC-SHA256 of the raw body"`* — the app has a secret and it
disagrees with yours. Now the values genuinely differ. Remember that IntelliJ run-configuration
variables and PowerShell `$env:` variables are separate worlds, so `-Secret $env:RAZORPAY_WEBHOOK_SECRET`
sends an empty string in a terminal where you never set it. Pass the literal value instead.

**Every webhook is `IGNORED` with "No recovery attempt matches".** `external_ref` is still null.
Redo step 7 and check the UPDATE actually returned a row.

**`UnexpectedRollbackException` in the log.** Should not happen — if it does, something is catching
a constraint violation inside the transaction that caused it. Send me the stack trace.

**Flyway checksum mismatch.** You edited a migration after it had been applied. This is demo data,
so the fix is to drop and recreate: `psql -U postgres -c "DROP DATABASE recoverai;"` then redo
step 2.

**Port 8080 already in use.** Something else is on it, possibly an app instance you forgot to stop.
`Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess` will tell you which process.
