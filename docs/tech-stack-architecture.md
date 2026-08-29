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

The optimization flow is:

```text
Charging Requirements
        ↓
Calculate Required Energy
        ↓
Calculate Effective Charging Power
        ↓
Calculate Required Charging Duration
        ↓
Evaluate Available Continuous Windows
        ↓
Feasible continuous window found?
        ├─ Yes → Select Lowest-Cost Continuous Window → Persist ChargingPlan (SUCCEEDED) → 201
        └─ No  → Persist ChargingPlan (FAILED, failure_reason) → 422
```

A **charging plan** represents the result of a single optimization attempt, not an execution state. `charging_plans.status` is limited to `SUCCEEDED` and `FAILED`:

- **SUCCEEDED** — a feasible continuous window was found. All recommendation fields (`recommendedStartAt`, `recommendedEndAt`, `expectedEnergyKwh`, `estimatedCostNok`, ...) are populated, and the API returns `201 Created` with the plan.
- **FAILED** — the request was valid but no feasible plan could be produced (e.g. insufficient time before the deadline, the energy requirement cannot be met, or no continuous slot is available). Recommendation fields are `NULL` and `failure_reason` records why. The API returns `422 Unprocessable Entity` instead of a `ChargingPlan`, so the success response schema never needs nullable recommendation fields.

FAILED attempts are kept in `charging_plans` for internal record-keeping only; `GET /charging-plans` and `GET /charging-plans/{planId}` only ever return SUCCEEDED plans. Unexpected system failures (unhandled exceptions, database errors) are handled as ordinary `5xx` responses and are not required to be persisted as a `charging_plans` row.

A **charging schedule** is created only after the user confirms a SUCCEEDED plan. Reservation and execution lifecycle (`CREATED`, `WAITING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `FAILED`) belongs entirely to `charging_schedules` / `ScheduleStatus`, not to `charging_plans`.

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
