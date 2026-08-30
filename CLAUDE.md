# WattPilot

## Project Overview

WattPilot is a smart energy optimization service designed to help users charge electric vehicles when electricity prices are lowest.

The initial version focuses on EV charging optimization using Norwegian time-based electricity price data.

The core service flow is:

1. Fetch electricity prices from an external API.
2. Calculate the optimal charging time based on EV battery information and charging requirements.
3. Create a charging schedule.
4. Simulate charging using a mock charging process instead of controlling a real vehicle.
5. Store charging history and calculate estimated savings.

---

## MVP Scope

### V1

* User signup and login
* EV registration
* Electricity price retrieval
* Optimal charging time calculation
* Charging scheduling
* Mock EV charging
* Charging history
* Estimated cost savings calculation

Actual vehicle control is **not included in V1**.

### V1.5

* Tibber API integration
* EV specification master data
* UX improvements

### V2

* Vehicle manufacturer integrations
* Notifications
* Home appliance optimization
* Smart home integrations
* Multiple device support

Do not implement future-version features unless explicitly requested by the user.

---

## Tech Stack

### Backend

* Java 21 LTS
* Spring Boot 4.1.1
* Gradle
* Spring Data JPA
* Hibernate
* Spring Security
* JWT authentication
* PostgreSQL
* Flyway
* Spring RestClient
* springdoc-openapi

### Testing

* JUnit 5
* Mockito
* Testcontainers

### Infrastructure

* Docker
* Docker Compose

### Frontend

* React 19
* TypeScript
* Vite
* Tailwind CSS
* shadcn/ui
* TanStack Query
* React Router
* Recharts

Do not introduce additional major frameworks, infrastructure components, or architectural patterns without first explaining why they are needed.

---

## Repository Structure

```text
wattpilot/
├── CLAUDE.md
├── README.md
│
├── docs/
│   ├── service-overview.md
│   ├── mvp-scope.md
│   ├── user-flow.md
│   ├── tech-stack-architecture.md
│   ├── deployment.md
│   ├── architecture.drawio
│   ├── database.dbml
│   ├── openapi.yaml
│   └── images/
│
├── backend/
│
└── frontend/
```

---

## Documentation Rules

Before implementing a feature, review the relevant documents under `/docs`.

The primary sources of truth are:

* `docs/mvp-scope.md` — product and version scope
* `docs/user-flow.md` — user and service flow
* `docs/database.dbml` — database design
* `docs/openapi.yaml` — REST API contract
* `docs/tech-stack-architecture.md` — technology stack, application architecture, package structure, and architecture principles
* `docs/deployment.md` — environments, deployment architecture, AWS infrastructure, configuration, and CI/CD
* `docs/architecture.drawio` — visual architecture reference

`CLAUDE.md` defines working rules and development behavior. Project design facts should not be duplicated here when a dedicated document already owns them.

When documents overlap, use the more specific document as the source of truth. In particular:

* Product scope → `docs/mvp-scope.md`
* API contract → `docs/openapi.yaml`
* Database schema → `docs/database.dbml`
* Application architecture and package structure → `docs/tech-stack-architecture.md`
* Deployment and environment strategy → `docs/deployment.md`

Do not silently change documented APIs, database structures, business rules, or product scope.

If implementation requires a design change, explain:

1. What needs to change
2. Why the change is necessary
3. Which documents or modules are affected

Do this before modifying the implementation.

---

## Language Rules

Conversation with the user should be conducted in **Korean by default**.

However, everything that is written into the repository must use **English**.

This includes, but is not limited to:

* Source code identifiers
* Class names
* Method names
* Variable names
* Package names
* Comments
* Javadoc
* TODO comments
* Exception messages
* Log messages
* Validation messages
* API response messages
* Configuration comments
* Database migration comments
* Test names
* Test descriptions
* Technical documentation added to the repository
* Git commit messages

Do not add Korean text to source code, configuration files, logs, exception messages, database migrations, or technical documentation.

Even when the user explains requirements in Korean, translate them into natural and professional English when applying them to the codebase.

Avoid unnecessary comments.

When comments are necessary, prefer explaining **why** something is implemented a certain way rather than restating **what** the code already does.

---

## Requirement Clarification Before Implementation

Before generating or modifying source code, verify that the requirement is sufficiently clear.

Do **not** immediately implement the feature when any important behavior is ambiguous.

Ask the user for clarification first when:

* A requirement can be interpreted in multiple ways.
* A business rule is unclear.
* API behavior is not clearly defined.
* Data persistence behavior is unclear.
* Important default values would need to be invented.
* The user request conflicts with existing documentation.
* The implementation choice would materially change user experience or system behavior.
* A database schema change may be required.
* An API contract change may be required.
* There are multiple reasonable architectural approaches with meaningful tradeoffs.

Questions should focus only on ambiguity that materially affects implementation.

Do not ask the user for information that can already be determined from:

* `CLAUDE.md`
* `/docs`
* Existing source code
* Existing tests
* Existing configuration

Once the requirement is sufficiently defined, proceed with implementation.

Do not ask unnecessary confirmation questions for requirements that are already clear.

Never invent business rules merely to complete an implementation.

---

## Backend Architecture

Use a modular monolith with a conventional layered Spring Boot architecture.

The package structure must follow `docs/tech-stack-architecture.md`.

Current V1 package structure:

```text
com.wattpilot
├── auth
├── user
├── ev
├── electricity
├── charging
├── scheduler
├── history
├── integration
└── common
    ├── config
    ├── security
    └── exception
```

Do not introduce an additional `domain` or `global` wrapper unless the architecture document is intentionally updated first.

Within each domain, prefer the following structure where appropriate:

```text
controller
service
repository
entity
dto
```

Do not create empty architectural layers only for the sake of following the structure.

Keep business logic out of controllers.

Controllers should primarily handle:

* HTTP request mapping
* Request validation
* Delegation to application or domain services
* HTTP response construction

Business logic should live in services or appropriate domain objects.

---

## Database Rules

PostgreSQL is the primary database.

Use Flyway for database schema migrations.

Migration files must be stored under:

```text
backend/src/main/resources/db/migration/
```

Example:

```text
V1__create_user_table.sql
V2__create_ev_table.sql
```

Do not manually modify production database schemas outside Flyway migrations.

Database changes must remain consistent with:

```text
docs/database.dbml
```

If a database change is required:

1. Explain the proposed schema change.
2. Confirm that it is consistent with the product requirement.
3. Update the Flyway migration.
4. Update `docs/database.dbml` when appropriate.

Do not add database columns solely for hypothetical future requirements.

---

## API Rules

The OpenAPI specification located at:

```text
docs/openapi.yaml
```

is the primary API contract.

Implementation should follow the documented:

* Paths
* HTTP methods
* Request schemas
* Response schemas
* HTTP status codes

Do not silently change the API contract.

If implementation requires an API contract change, explain the change before making it.

Do not expose JPA entities directly through API responses.

Use dedicated request and response DTOs.

---

## Coding Guidelines

* Prefer simple, readable, and maintainable implementations.
* Avoid unnecessary abstractions.
* Do not over-engineer for hypothetical future requirements.
* Follow existing project conventions before introducing new patterns.
* Use meaningful domain-oriented names.
* Avoid overly generic names such as `Utils`, `Manager`, or `Helper` unless clearly justified.
* Keep methods focused on a clear responsibility.
* Prefer constructor injection.
* Do not use field injection.
* Use DTOs for API boundaries.
* Do not expose persistence entities directly to clients.
* Prefer explicit business logic over clever or difficult-to-understand implementations.
* Avoid introducing dependencies unless they provide clear value.
* Keep changes scoped to the requested feature.
* Do not refactor unrelated code during feature implementation unless explicitly requested or necessary to complete the task safely.

---

## Testing Rules

Add or update tests when they provide meaningful protection for the implemented behavior.

Prefer tests for:

* Business logic
* Charging optimization logic
* Pricing calculations
* Scheduling behavior
* API validation
* Important edge cases
* Bug fixes

Use:

* JUnit 5
* Mockito where isolation is useful
* Testcontainers where real PostgreSQL behavior matters

Do not create superficial tests solely to increase test count.

When a feature is completed, verify relevant tests before considering the work complete.

---

## Development Workflow

When starting a new task:

1. Read `CLAUDE.md`.
2. Review the relevant files under `/docs`.
3. Inspect the existing implementation.
4. Inspect relevant tests and configuration.
5. Determine whether any requirement is ambiguous.
6. Ask the user about meaningful ambiguity before generating code.
7. Identify the affected modules and files.
8. Explain significant architecture, API, or database changes before making them.
9. Implement the smallest coherent change that satisfies the confirmed requirement.
10. Add or update relevant tests.
11. Verify the implementation.
12. Check consistency with existing documentation and API contracts.
13. Identify whether the completed work represents a meaningful Git commit checkpoint.

---

## Git and Commit Rules

All Git commit messages must be written in English.

Prefer concise commit messages that clearly describe the completed change.

Use Conventional Commit-style prefixes where appropriate.

Examples:

```text
feat: add EV registration API
feat: implement electricity price retrieval
feat: add charging optimization service
fix: handle missing electricity price data
refactor: simplify charging schedule calculation
test: add optimization service tests
docs: update API specification
chore: configure local PostgreSQL environment
```

### Commit Message Format

* Format: `type: subject`
* Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`. Use the closest match; do not invent new types without discussing it first.
* Choose the type based on what actually changed in that commit, not the broader task it belongs to (e.g. a docs-only change is `docs:` even if it supports an in-progress feature).
* Subject: imperative mood (e.g. "add", "fix", "simplify", not "added" or "adds"), lowercase start, no trailing period, ideally under ~72 characters.
* When one commit bundles multiple related changes that the subject line cannot fully capture, add a body as `-`-prefixed bullet points, one change per line. Skip the body when the subject alone already says everything.
* If unrelated changes end up staged together (e.g. project scaffolding and documentation updates), prefer splitting them into separate commits with their own type/subject rather than writing one combined commit.
* Do not include tool-generated trailers (e.g. `Co-Authored-By`, session links) in manually authored commits.

Do not create commits automatically unless the user explicitly requests it.

The user is responsible for reviewing the changes and performing the final commit.

Never automatically:

* Run `git commit`
* Run `git push`
* Merge branches
* Rebase branches
* Reset Git history
* Rewrite Git history
* Delete branches

unless explicitly requested by the user.

### Commit Checkpoint Reminder

When a meaningful and logically complete unit of work has been completed and verified, remind the user that it may be a good point to create a commit.

A good commit checkpoint usually means:

* A feature or meaningful sub-feature has been completed.
* The implementation is in a working state.
* Relevant tests or validation have been completed.
* The change forms a coherent unit that can be understood independently.
* No immediate unfinished work is required for the feature to function as intended.

Do not suggest a commit after every minor edit.

If multiple small changes belong to the same logical feature, treat them as a single commit checkpoint.

When suggesting a commit, also provide a recommended English commit message.

Example:

```text
The EV registration feature is complete and verified.

Suggested commit:
feat: add EV registration
```

Commit suggestions are recommendations only.

The final decision and actual commit are always performed by the user.

---

## Important V1 Business Constraints

* V1 does not control real EVs.
* Use mock charging instead of vehicle manufacturer APIs.
* Actual manufacturer integrations belong to a future version.
* Charging optimization in V1 uses continuous charging periods.
* Charging efficiency may be treated as a system-level constant where defined by the current specification.
* Do not implement V1.5 or V2 functionality unless explicitly requested.
* Do not add smart-home appliance support to V1.
* Do not introduce microservices for the MVP.
* Do not introduce Kubernetes for the MVP.
* Keep the initial architecture as a modular monolith.
* Prefer PostgreSQL with Docker Compose for local development.
* Do not change documented business rules without explicitly discussing the change.
* Do not invent unspecified business rules.
* Do not add Korean text to repository content.

---

## General Decision Principles

When multiple implementations are technically valid, prefer the option that is:

1. Simpler
2. Easier to explain
3. Easier to test
4. Appropriate for the current MVP scope
5. Consistent with existing documentation
6. Maintainable by a single developer

Avoid adding complexity only to make the project appear more sophisticated.

WattPilot is intended to demonstrate sound backend engineering, clear business logic, maintainable architecture, and practical system design rather than unnecessary technical complexity.
