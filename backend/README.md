# RecoverAI — Backend

Spring Boot 3.3.4 / Java 21 / PostgreSQL / Flyway.

## Status: Phase 2 complete (Backend Foundation)

What exists right now:
- Full JPA entity model + Flyway migration (`V1__init_schema.sql`) matching the architecture doc
- Repositories for all 7 tables
- Read-side services + REST controllers for: recovery cases (list/detail), policies (view/edit),
  audit logs, agent decisions (recent feed), analytics summary, health check
- `AuditService` — the single write path for `audit_log`, ready for Phase 3 agents to call
- `PolicyRuleService` — typed getters (`getInt`, `getDecimal`, `getBoolean`) the Policy Engine will use
- WebClient beans pre-wired for both Grok API and Razorpay (Test Mode), auth headers included
- Global exception handling with clean JSON error bodies

What's intentionally **not** here yet (Phase 3+):
- Detection / Diagnosis / Strategy agents
- Policy Engine (the deterministic allow/block logic itself)
- Recovery Executor (real Razorpay calls + simulated actions)
- `/api/batch/process`, `/api/events` ingestion endpoints
- SSE live agent activity stream

## Running locally

1. Start Postgres and create a database named `recoverai` (or point `DB_URL` elsewhere).
2. Copy `.env.example` to `.env` and fill in real values (or export the vars directly).
3. `mvn spring-boot:run`
4. Flyway will auto-run `V1__init_schema.sql` on first boot, seeding the 7 default policy rules.
5. Check `GET http://localhost:8080/api/health`

## Package layout

```
com.recoverai
├── config       WebConfig (CORS), WebClientConfig (Grok + Razorpay clients)
├── controller   REST endpoints
├── service      Read/write services not owned by a specific agent
├── repository   Spring Data JPA repositories
├── entity       JPA entities + entity.enums
├── dto          request / response records
├── agent        (Phase 3) Detection/Diagnosis/Strategy agents
├── policy       (Phase 3) deterministic Policy Engine
├── razorpay     (Phase 4) Razorpay Test Mode client + executor
├── audit        AuditService (append-only audit trail)
├── analytics    AnalyticsService (live KPI aggregation, never hardcoded)
└── exception    Custom exceptions + GlobalExceptionHandler
```
