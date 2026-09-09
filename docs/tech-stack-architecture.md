# Overview

WattPilot V1 will use a **modular monolith** architecture with a separate React frontend, Spring Boot backend, and PostgreSQL database. The goal is to keep the MVP simple while maintaining clear domain boundaries and allowing future integrations such as Tibber and vehicle manufacturer APIs.

# Tech Stack

| Area | Technology |
| --- | --- |
| Backend Language | Java 21 LTS |
| Backend Framework | Spring Boot 4.x |
| API | REST API, OpenAPI 3.x YAML |
| Security | Spring Security, JWT, BCrypt |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| DB Migration | Flyway |
| Scheduling | Spring Scheduler |
| External HTTP Client | Spring RestClient |
| Backend Testing | JUnit 5, Mockito, Testcontainers |
| Frontend | React 19, TypeScript, Vite |
| UI | Tailwind CSS, shadcn/ui |
| Server State | TanStack Query |
| Routing | React Router |
| Charts | Recharts |
| Build | Gradle, npm |
| Container | Docker, Docker Compose |
| Source Control / CI-CD | GitHub, GitHub Actions |
| Cloud | AWS |

# Application Architecture

The backend will follow a **domain-oriented modular monolith** structure rather than a microservices architecture.

```text
com.wattpilot
├─ auth
├─ user
├─ ev
├─ electricity
├─ charging
├─ scheduler
├─ history
├─ integration
└─ common
```

Each domain may contain its own controller, service, repository, domain model, and DTOs. This keeps business logic grouped by feature instead of organizing the entire application only by technical layer.

# Core Architecture Principles

- **Modular Monolith First** — Microservices are intentionally excluded from V1 to avoid unnecessary operational complexity.
- **Domain-oriented Structure** — Business domains such as EV, Charging, and Electricity are separated clearly.
- **External API Isolation** — External providers are accessed through dedicated clients or interfaces so implementations can be replaced later.
- **Database as Source of Truth** — Charging plans, schedules, and execution states are persisted in PostgreSQL.
- **Configuration over Hardcoding** — Environment-specific values and system constants are managed through application configuration.
- **UTC Storage** — Timestamps are stored in UTC and converted to `Europe/Oslo` when displayed.

# External Integrations

V1 will use the **Hva koster strømmen API** for hourly electricity prices.

```text
ElectricityPriceService
        ↓
ElectricityPriceProvider
        ↓
HvaKosterStrommenClient
```

The provider interface should allow future implementations such as Tibber without changing the core charging optimization logic.

Vehicle control follows the same approach.

```text
VehicleController
        ↓
MockVehicleController   // V1
        ↓
Manufacturer APIs       // Future
```

V1 uses Mock charging only. Real vehicle control is intentionally excluded.

# Database Management

PostgreSQL is the primary relational database. Schema changes are managed using **Flyway migration scripts** rather than manually modifying the database.

```text
src/main/resources/db/migration/
├─ V1__create_users.sql
├─ V2__create_evs.sql
├─ V3__create_electricity_prices.sql
└─ V4__create_charging_tables.sql
```

Flyway applies unapplied migrations automatically when the application starts and records migration history in the database.

Hibernate schema management should use validation rather than automatic schema updates in production-like environments.

V1 uses `BIGINT` primary keys and foreign keys. API resource identifiers use the same `int64` representation to keep the persistence and API models simple and consistent.

# Scheduling

Spring Scheduler is sufficient for V1.

Two main scheduled processes are expected:

- Fetch and store electricity price data.
- Detect scheduled charging reservations and execute Mock charging at the required time.

Kafka, RabbitMQ, and other message brokers are not required for the MVP.

# Charging Optimization

The charging optimization logic should be implemented as an independent domain service.

Main inputs include:

- Battery capacity
- Current battery level
- Target battery level
- Maximum AC charging power
- Default charger power
- Charging efficiency
- Available charging window
- Hourly electricity prices

The effective charging power is the lower value of the EV's maximum AC charging power and the configured charger power.

```text
effectiveChargingPowerKw = min(maxAcChargingPowerKw, defaultChargerPowerKw)
```

For V1, the default charging efficiency is configured as `0.9` and is not stored per EV in the database.

V1 supports **continuous charging only**. The optimizer selects one contiguous charging window that satisfies the required charging duration and completion deadline.

The selected charging window may internally consist of multiple hourly electricity-price slots, but those slots must be consecutive.

The flow has two steps — a non-persisting preview and a persisting confirm — over one shared calculation:

```text
Charging Requirements
        ↓
Calculate Required Energy → Effective Charging Power → Required Charging Duration
        ↓
Enumerate every feasible continuous window, cost each one
        ↓
──────────────── POST /charging-plans/preview ────────────────
Rank by cost, return up to 3 candidates (nothing persisted)
  no feasible window → 422 (CHARGING_DEADLINE_TOO_SOON / CHARGING_PRICE_DATA_INSUFFICIENT /
                            CHARGING_NO_CONTINUOUS_WINDOW)
        ↓  (user picks one candidate)
──────────────── POST /charging-schedules ────────────────────
Re-run the calculation against the latest prices
  match the pick against a current candidate → 409 CHARGING_CANDIDATE_UNAVAILABLE if gone
  EV already has an overlapping active schedule → 409 CHARGING_SCHEDULE_CONFLICT
        ↓
Persist, in ONE transaction:
  1 charging_plans row (SUCCEEDED)  +  its charging_plan_slots  +  1 charging_schedules row (WAITING)
        ↓
201 with the schedule
```

The **calculation core** (`ChargingWindowCalculator`, pure; `ChargingOptimizationService`, orchestration) is shared: the preview and the confirm run identical code with no DB side effects. Only `ChargingScheduleService` writes.

A **charging plan** is the recommendation for the one candidate the user confirmed. `charging_plans.status` is `SUCCEEDED` for every plan the flow writes. `FAILED` stays a valid schema/DB value (the CHECK constraints support it) but is not written — an infeasible preview is simply a 422 and persists nothing. Unexpected system failures are ordinary `5xx` responses and are not persisted.

`GET /charging-plans` and `GET /charging-plans/{planId}` return the caller's stored (SUCCEEDED) plans.

A **charging schedule** is the execution booking for a plan, created in the same transaction as the plan, directly in `WAITING`. The `charging_schedules` table has no `user_id` / `ev_id`; ownership and the overlap check reach the EV through `charging_plan_id → charging_plans`. Reservation/execution lifecycle (`WAITING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `FAILED`) belongs to `charging_schedules` / `ScheduleStatus`. A confirm locks the EV row for the transaction so two concurrent confirms for the same EV cannot both pass the overlap check. `POST /charging-schedules/{scheduleId}/cancel` moves a `WAITING` schedule to `CANCELLED`; any other status is a 409 — V1 does not support cancelling a schedule once execution has started.

`GET /charging-schedules` is the **Schedules overview** — deliberately not a paginated list. It returns three blocks: `upcoming` (`WAITING`, earliest start first) and `inProgress` (`IN_PROGRESS`) as full schedule views, and `recentActivity` — the last `ChargingScheduleService.RECENT_ACTIVITY_LIMIT` (5) `COMPLETED`/`FAILED` schedules as a slim view (schedule/session id, EV name, times, status, `actualEnergyKwh`), most recently finished first. The cap is a **role boundary**: Schedules stays a "current and near-future operations" screen, and the full record of past charges — with costs, savings and per-hour detail — is the charging-history endpoints. Letting completed rows accumulate here would make the two features indistinguishable. `getSchedulesOverview` runs one query for the active schedules and one `Limit`-bounded query for the recent tail, then batch-loads the plans/slots/sessions they need.

# Charging Execution

A 1-minute scheduler drives every confirmed reservation through Mock Charging, calling an internal service directly — never its own HTTP API — so the whole flow stays inside one process for V1.

```text
ChargingExecutionScheduler   (com.wattpilot.scheduler, @Scheduled every minute)
        ↓ calls directly, no HTTP
ChargingExecutionService     (com.wattpilot.charging.service — one schedule id, own transaction)
        ↓
ChargingExecutionPort        (interface)
        ↓
MockChargingAdapter          // V1, succeeds unless a failure is injected by config
        ↓
Manufacturer APIs            // Future
```

Each tick reads three disjoint sets of schedule ids (bounded to `batch-size`, default 100) and processes completion before start before missed:

```text
complete: IN_PROGRESS AND scheduledEndAt <= now AND not backing off
start:    WAITING     AND scheduledStartAt <= now AND scheduledEndAt > now AND not backing off
missed:   WAITING     AND scheduledEndAt <= now                              (backoff state ignored: a closed window is a hard deadline)
```

Every schedule id gets its **own transaction** (`REQUIRES_NEW`) inside `ChargingExecutionService`; a failure on one id is logged and the batch continues with the next. A `ChargingSession` is created exactly once per schedule, at the moment a definitive outcome (success or a confirmed failure) is known — never per retry attempt — so `charging_sessions.charging_schedule_id` stays unique.

**Two failure kinds are handled differently:**

- **Business/execution failure** — `ChargingExecutionPort` returns a definitive failure (`CHARGER_UNAVAILABLE`, `VEHICLE_DISCONNECTED`, `START_REJECTED`, `CHARGING_INTERRUPTED`), or the window closes before a start was ever attempted (`MISSED_EXECUTION_WINDOW`). The schedule and session move to `FAILED` with that `failureCode`/`failureReason` and the transaction commits.
- **Transient technical error** — `ChargingExecutionPort` throws. Caught inside the same transaction, so a bounded retry (`retry-max-attempts`, default 3, with a backoff that starts at `retry-initial-backoff` and grows by `retry-backoff-multiplier` each attempt) commits normally; the schedule stays in its current status via `retry_count` / `next_retry_at`. Exhausting the budget finalizes both as `FAILED` with `failureCode = SYSTEM_ERROR` and a safe, generic `failureReason` — the underlying exception is logged, never exposed.
- **Database/transaction failure** (not from the port call) is deliberately left uncaught: the whole attempt rolls back, nothing is persisted — not even a retry-count increment — and the next tick's read-state query retries it for free, with no attempt limit.

Concurrency (a scheduler tick racing a user's cancel request, or two ticks touching the same schedule) is serialized with the same `SELECT ... FOR UPDATE` row-lock pattern `EvRepository` already uses, re-checking the expected status after acquiring the lock before making any change.

On a successful completion, `actualEnergyKwh` / `actualCostNok` / `baselineCostNok` / `optimizedCostNok` / `estimatedSavingsNok` are derived from the **plan/slot snapshot taken at confirmation time** — never a fresh price lookup or a re-run of the optimizer. V1 mock charging always finishes exactly as planned, so there is no partial-charge simulation.

`MockChargingAdapter` always succeeds unless a schedule id is listed in `wattpilot.charging.execution.mock.failures` (empty in every committed profile). That map assigns a `ChargingFailureCode` to a schedule so demos and integration tests can drive a specific `FAILED` outcome or the retry path deterministically — `SYSTEM_ERROR` makes the adapter throw (exercising the bounded retry), the others return a business failure on their phase. See `docs/charging-execution-states.md` §7.

`GET /charging-schedules/{scheduleId}` and the list endpoint embed the schedule's `ChargingSessionSummary` (null until the first execution attempt) — there is no user-facing Mock Charging API in V1; execution is only ever driven by the scheduler.

# Charging History

`com.wattpilot.history` owns the record of past charges and their savings performance — a role distinct from Schedules (current/near-future operations). It is a **read model**, no new table: `ChargingHistoryRepository` runs projection queries that join `charging_sessions → charging_schedules → charging_plans` (the plan carries the owning user and the `ev_name` snapshot taken at confirmation), so results are user-scoped, have no N+1, and never read the mutable `evs` row. Both endpoints answer the same questions Schedules cannot: how much have I charged, how much have I saved, why did the optimizer pick that window.

**`GET /charging-history`** — paginated list plus a `summary` header.

- **Scope:** `charging_session_status` `COMPLETED` and `FAILED` only. A schedule still `WAITING`, one currently charging (session `STARTED`), and a reservation cancelled before it ran belong to the Schedules overview, not here. `CANCELLED` sessions are not written in V1.
- **Order:** fixed `created_at DESC, id DESC` in the query; the endpoint ignores any client `sort`. `created_at` is the only timestamp present on every session (a `FAILED` session has no `completed_at`, a missed one has no `started_at`), and is exposed on both responses as `recordedAt` so the list always has a timestamp to show.
- **Filters:** optional `evId` (narrows list **and** summary) and `status` (narrows the list only; a value outside the two history statuses matches nothing).
- **Pagination:** `page` / `size` (capped at 100 by `spring.data.web.pageable.max-page-size`) with the standard `PageMetadata`; the response adds `summary` alongside `content`.

**`GET /charging-history/{sessionId}`** — the "charging receipt". Loads the session + schedule + plan + `charging_plan_slots` (the same style `ChargingScheduleService.getSchedule` uses) into three deliberately separate groups: **conditions** (start/target SOC, price area, deadline, EV snapshot), **plan** (recommended window, `plannedEnergyKwh`, `optimizedCostNok`, `baselineCostNok`, `estimatedSavingsNok`, per-hour `plannedSlots`), and **actual** (`actualEnergyKwh`, `actualCostNok`, `realizedSavingsNok`). A `FAILED` entry keeps the full plan (the plan succeeded — only execution failed) but has no `actual` figures. `CHARGING_HISTORY_NOT_FOUND` (404) for an unknown id, another account's session, or a non-terminal one.

**Planned vs. realized — never conflated.** From `ChargingResultCalculator`, a `COMPLETED` session stores both the plan's expected figures and the realized ones:

| meaning | field | source |
| --- | --- | --- |
| optimizer's expected cost | `optimizedCostNok` | `charging_sessions.optimized_cost_nok` (= plan `estimated_cost_nok`) |
| predicted saving | `estimatedSavingsNok` | `baselineCostNok - optimizedCostNok` |
| realized cost / energy | `actualCostNok` / `actualEnergyKwh` | `charging_sessions.actual_*`, recorded on completion |
| **delivered saving** | `realizedSavingsNok` | `baselineCostNok - actualCostNok` |

History treats **`realizedSavingsNok` as the saving** — the per-item value and the summary's `totalSavingsNok` both use `baseline - actual`, never the estimate. In V1 mock charging finishes exactly as planned so the two are numerically equal, but the concepts stay separate (the DB column `estimated_savings_nok` is left as-is; the distinction lives in the DTOs). `plannedSlots` are the plan's per-hour rows from `charging_plan_slots` and are named to make clear they are planned, not a per-hour execution result — V1 stores no per-slot actual, and the deprecated `charging_schedule_slots` table is unused.

**Summary aggregation** lives in `ChargingHistoryRepository` (one query: session counts over `COMPLETED`+`FAILED`, plus `SUM(actualEnergyKwh)` and `SUM(baselineCostNok - actualCostNok)` over the `COMPLETED` rows), so a later dashboard / savings-summary feature can reuse it. `successRate` = `COMPLETED` / total, one decimal.

- **Indexes:** the join is covered by `idx_charging_plans_user_created`, `uq_charging_schedules_plan`, and `uq_charging_sessions_schedule`; no new index or migration is added for V1.

# Deployment Architecture

```text
User
 ↓
CloudFront + S3
React Frontend
 ↓ HTTPS / REST
ECS Fargate
Spring Boot Backend
 ↓
RDS PostgreSQL
```

Recommended AWS services:

- **S3 + CloudFront** — Frontend hosting
- **ECS Fargate** — Spring Boot container hosting
- **ECR** — Docker image registry
- **RDS PostgreSQL** — Production database
- **CloudWatch** — Application logs and monitoring
- **Route 53** — DNS
- **AWS Certificate Manager** — HTTPS certificates

GitHub Actions will handle automated test, build, Docker image creation, and deployment.

# V1 Non-Goals

The following technologies are intentionally excluded from V1 unless a concrete need appears:

- Kubernetes
- Kafka / RabbitMQ
- Redis
- Elasticsearch
- Microservices
- GraphQL
- WebFlux

Keeping the architecture simple allows the project to focus on WattPilot's core value: electricity-price-based charging optimization and reliable scheduling.
