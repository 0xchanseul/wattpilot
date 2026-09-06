# Charging Execution: States, Failure Codes, and Handling

Reference for the Mock Charging execution scheduler introduced alongside `ChargingExecutionScheduler`,
`ChargingExecutionService`, `ChargingExecutionPort`, and `MockChargingAdapter`. Covers
`charging_schedules` (`ChargingSchedule` / `ChargingScheduleStatus`) and `charging_sessions`
(`ChargingSession` / `ChargingSessionStatus` / `ChargingFailureCode`).

See also: `docs/tech-stack-architecture.md` ("Charging Execution" section) for the class/package
diagram, `docs/database.dbml` for the full schema, `docs/openapi.yaml` for the API shape.

---

## 1. Two state machines, one relationship

- **`ChargingSchedule`** is the reservation. It is created directly in `WAITING` when a user confirms a
  previewed candidate (`POST /charging-schedules`), and is driven to a terminal status
  (`COMPLETED` / `CANCELLED` / `FAILED`) either by the user (cancel) or by the scheduler (execution).
- **`ChargingSession`** is the record of one execution *attempt's outcome*. It is created **exactly
  once** per schedule — not once per retry — at the moment a definitive result is known: success, a
  business failure, or a retry-exhausted system failure. A schedule that is cancelled while still
  `WAITING` never gets a session at all, because cancellation happens before any execution attempt.
- Invariant enforced by `uq_charging_sessions_schedule` (`charging_schedule_id` unique): a schedule has
  **at most one** session, ever.

```
ChargingSchedule: WAITING → IN_PROGRESS → COMPLETED
                     |            |
                     |            └──────→ FAILED
                     └──────────────────→ CANCELLED   (only while WAITING)
                     └──────────────────→ FAILED       (missed window, or start failed)

ChargingSession:  (created on a definitive start outcome) → STARTED → COMPLETED
                                                                    └─→ FAILED
                  (created directly, no STARTED phase)    → FAILED   (business/system start failure, or missed window)
```

---

## 2. `ChargingScheduleStatus`

| Status | Meaning | Entered from | Can still be cancelled? |
|---|---|---|---|
| `WAITING` | Confirmed, not yet started. | Created here directly (no `CREATED` state — removed, see §6). | Yes |
| `IN_PROGRESS` | Mock Charging start succeeded; a session is `STARTED`. | `WAITING`, via a successful start attempt. | No (V1 does not support cancelling a running execution) |
| `COMPLETED` | Mock Charging completion succeeded. | `IN_PROGRESS`, via a successful completion attempt. | — (terminal) |
| `CANCELLED` | User cancelled before execution began. | `WAITING` only, via `POST /charging-schedules/{id}/cancel`. | — (terminal) |
| `FAILED` | Execution failed, or the window closed before it ever started. | `WAITING` (start failure or missed window) or `IN_PROGRESS` (completion failure). | — (terminal) |

`retry_count` / `next_retry_at` are bookkeeping columns on `charging_schedules`, not statuses; they
track a transient-error backoff within `WAITING` or `IN_PROGRESS` (see §5) and are always cleared
(`next_retry_at = null`) on any terminal or `IN_PROGRESS` transition.

---

## 3. `ChargingSessionStatus`

| Status | Meaning | `started_at` | Outcome fields (`actual*`, `baseline/optimized/estimatedSavings`) | Failure fields |
|---|---|---|---|---|
| `STARTED` | Execution is running. | set | null | null |
| `COMPLETED` | Execution finished as planned. | set | set (from the plan/slot snapshot, never recomputed) | null |
| `FAILED` | A definitive business/system failure, or the window was missed. | set if it had actually started (a completion-phase failure); null if it never started (a start-phase failure or `MISSED_EXECUTION_WINDOW`) | null | set (`failure_code` + `failure_reason`, both required) |
| `CANCELLED` | Reserved for a future version. | — | — | — |

`CANCELLED` is a valid schema/enum value (`charging_session_status`) but **is not written by V1**:
execution-time cancellation is out of scope, and a schedule cancelled while `WAITING` never reaches the
point where a session would be created. This mirrors how `charging_plans.status` keeps `FAILED` as a
valid value the preview/confirm flow simply never writes.

Enforced in the database by four CHECK constraints (`V4__charging_session_execution_fields.sql`):
`charging_session_started_fields_valid`, `..._completed_fields_valid`, `..._failed_fields_valid`,
`..._cancelled_fields_valid`. Each requires the exact field-presence pattern above for its status —
independent of and additional to `charging_plans`' own SUCCEEDED/FAILED constraints, which describe the
optimizer's recommendation, not the execution outcome.

---

## 4. `ChargingFailureCode`

Stored in `charging_sessions.failure_code` (`VARCHAR(50)` + CHECK, not a DB enum type — same pattern as
`charging_plans.status`). Always paired with a human-readable `failure_reason`.

| Code | Meaning | Set by |
|---|---|---|
| `CHARGER_UNAVAILABLE` | The charger could not be reached/reserved. | `ChargingExecutionPort` business result (start phase) |
| `VEHICLE_DISCONNECTED` | The vehicle was not connected when charging should have started. | `ChargingExecutionPort` business result (start phase) |
| `START_REJECTED` | The start command was rejected. | `ChargingExecutionPort` business result (start phase) |
| `CHARGING_INTERRUPTED` | Charging was in progress but did not finish successfully. | `ChargingExecutionPort` business result (completion phase) |
| `MISSED_EXECUTION_WINDOW` | The schedule's window closed while still `WAITING` — it was never attempted. | `ChargingExecutionService.markMissed` |
| `SYSTEM_ERROR` | A transient technical error (an exception from `ChargingExecutionPort`) exhausted its retry budget. | `ChargingExecutionService` retry exhaustion (start or completion phase) |

The first four are the *Mock Charging adapter's own vocabulary* — V1's `MockChargingAdapter` never
returns them (it always succeeds; see §7), but the port contract and the CHECK constraint support them
for whichever component next implements a real failure path. `SYSTEM_ERROR`'s `failure_reason` is
always a fixed, generic sentence — never the underlying exception message or stack trace, which is
logged server-side only (same principle as `GlobalExceptionHandler`'s handling of unexpected `5xx`s).

---

## 5. Two kinds of failure, handled differently

### Business / definitive failure
`ChargingExecutionPort.start()` / `.complete()` **returns** `ExecutionOutcome.Failure(code, reason)`.

```
Session  → FAILED (failure_code, failure_reason set)
Schedule → FAILED
COMMIT
```
No retry — the outcome is final by construction.

### Transient technical error
`ChargingExecutionPort.start()` / `.complete()` **throws** an unchecked exception. Caught inside
`ChargingExecutionService`, in the same transaction:

- **Attempts remaining** (`retry_count + 1 < retry-max-attempts`, default 3): the schedule stays in its
  current status; `retry_count` increments and `next_retry_at` is set to
  `now + retry-initial-backoff × retry-backoff-multiplier^(attempt-1)` (defaults: 1m, 2m, 4m). The
  session is **not** touched (it may not even exist yet, if this is a start-phase retry). Commits
  normally.
- **Budget exhausted**: schedule → `FAILED`; session → `FAILED` with `SYSTEM_ERROR` (created fresh if
  this was a start-phase exhaustion, or updated in place if a completion-phase session already existed
  as `STARTED`). Commits normally.

### Database / transaction failure
Any failure **not** from the port call (e.g. the row lock, an entity save, or the commit itself) is
**not caught** — it propagates, the whole transaction rolls back, and *nothing* is persisted, not even
a `retry_count` increment. The next scheduler tick's read-state query naturally re-selects the same
schedule and retries the entire attempt "for free" — this path has no attempt limit, because there is
no durable record that an attempt was ever made.

### Missed window
Independent of the two failure kinds above: if a `WAITING` schedule's `scheduled_end_at` arrives before
it was ever successfully started (scheduler downtime, retries not resolved in time, batch backlog,
etc.), `ChargingExecutionService.markMissed` finalizes it directly:
```
Session  → FAILED (failure_code = MISSED_EXECUTION_WINDOW, started_at = null)
Schedule → FAILED
COMMIT
```
This check ignores `next_retry_at` deliberately — a closed window is a hard deadline regardless of
backoff state.

---

## 6. Design decisions worth remembering

- **`CREATED` was removed.** A confirmed schedule used to be created in `CREATED` with no distinct
  behavior from `WAITING`; it is now created directly in `WAITING`. Removed from the Java enum, the
  PostgreSQL enum type (`V3__remove_created_schedule_status_and_add_retry.sql`, which recreates the
  type since PostgreSQL cannot drop a single enum value), and `docs/openapi.yaml`'s `ScheduleStatus`.
- **Cancellation is `WAITING`-only.** V1 does not support cancelling `IN_PROGRESS` execution. Attempting
  to cancel anything but a `WAITING` schedule returns `409 CHARGING_SCHEDULE_NOT_CANCELLABLE`.
- **Concurrency** (a scheduler tick racing a cancel request, or two ticks touching the same schedule) is
  serialized with `SELECT ... FOR UPDATE` (`ChargingScheduleRepository.findByIdForUpdate`), the same
  pattern `EvRepository` uses for EV row locking — the loser of the race re-reads the now-changed status
  and no-ops instead of double-applying a transition.
- **Snapshot-based results.** `actualEnergyKwh` / `actualCostNok` / `baselineCostNok` /
  `optimizedCostNok` / `estimatedSavingsNok` on a `COMPLETED` session come from the `ChargingPlan`
  snapshot taken at confirmation time (`ChargingResultCalculator`) — never a fresh price lookup or a
  re-run of the optimizer. V1 has no partial-charge simulation, so a completed session always matches
  the plan's expected figures exactly.
- **No user-facing Mock Charging API.** The scheduler calls `ChargingExecutionService` in-process; there
  is no `/mock-charging/*` HTTP surface. Clients see the outcome through
  `ChargingSchedule.session` (`ChargingSessionSummary` in the API, null until the first attempt).
