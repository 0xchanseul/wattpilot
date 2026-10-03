| Feature | V1 | V1.5 | V2 | Description | Notes |
| --- | --- | --- | --- | --- | --- |
| Sign-up / Login | ✅ |  |  | Basic authentication |  |
| Profile Update |  | ✅ |  | Update name and default price area | Not part of the V1 user flow |
| EV Registration | ✅ |  |  | Battery capacity, maximum AC charging power, default charger power | Manual input in V1 |
| Electricity Price Lookup | ✅ |  |  | External API | Hva koster strømmen API |
| Optimal Charging Time Calculation | ✅ |  |  | Core feature | Continuous charging window only; preview returns up to 3 candidates and persists nothing |
| Charging Reservation | ✅ |  |  | Scheduler | The user confirms one previewed candidate; only that candidate is stored as plan + slots + schedule. A reservation can be cancelled only while still `WAITING`. `GET /charging-schedules` is an overview (`upcoming` / `inProgress` / `recentActivity` — last 5 finished), not a long history. |
| Actual EV Control | ❌ |  |  | Use Mock |  |
| Mock Charging | ✅ |  |  | Used instead of actual devices | Triggered internally by a 1-minute execution scheduler, not by a user-facing API; a configurable random rate (10% by default, per session) makes some reservations fail instead of always succeeding, and a schedule id can still be pinned to an exact outcome via config for a deterministic demo or test |
| Charging History | ✅ |  |  | `GET /charging-history`, `GET /charging-history/{sessionId}` | Read model over `charging_sessions`/`charging_schedules`/`charging_plans` (no new table). COMPLETED/FAILED only, newest first, with a realized-savings `summary` header. Detail is a "charging receipt": conditions + plan + per-hour breakdown + realized outcome. |
| Savings Calculation | ✅ |  |  |  |  |
| Vehicle Telemetry (read-only) |  | ✅ |  | Optional Smartcar connection per EV | Live battery %, range, plug/charging status via Smartcar API v3, requesting only `read_battery`/`read_charge`. Replaces the originally-planned Tibber integration (see TODO.md, not tracked by Git, section 3.3). The live battery % prefills the editable "current battery" field when planning charging. Never affects charging execution - Mock Charging remains the only execution path |
| Vehicle Specification Master Data |  | ✅ |  | Build master data for vehicle specifications | Automatically display vehicle specifications when the user selects only the vehicle model |
| Manufacturer Integration (control) |  |  | ✅ | Vehicle manufacturer control integration | Actual charge start/stop commands through supported vehicle manufacturers via OAuth. Read-only telemetry is already covered above (V1.5) |
| Notifications |  |  | ✅ |  |  |

- **Login session lifetime:** Access tokens live 30 minutes and are never kept in browser persistent storage. A session has an absolute expiration fixed at login — 7 days by default, or 30 days when the user ticks "keep me signed in" on the login form (default off). Refresh-token rotation issues a new token but never moves that deadline, so an active user is still forced to log in again once the original expiry passes. Server restarts do not end sessions (refresh tokens live in PostgreSQL).
- **V1 EV input:** Users manually enter battery capacity, maximum AC charging power, and default charger power.
- **V1 charging optimization:** Only continuous charging windows are supported. The selected price slots must be consecutive.
- **Preview vs. confirm:** `POST /charging-plans/preview` calculates up to three cheapest candidates and writes nothing to the database. `POST /charging-schedules` re-runs the calculation against the latest prices, keeps only the candidate the user selected, and stores it as one `charging_plans` row, its `charging_plan_slots`, and one `charging_schedules` row in a single transaction. Unselected candidates and infeasible previews are never persisted.
- **Server is authoritative:** the confirm request sends only the original conditions and the selected start/end instants — never a cost, energy or slot list. Every stored figure is recomputed by the server.
- **Charging plan vs. reservation:** A charging plan is the optimizer's recommendation for the confirmed candidate. A charging schedule (reservation) is its execution booking, created together with the plan.
- **Schedules vs. History:** Schedules (`GET /charging-schedules`) is a current/near-future operations view — `upcoming`, `inProgress`, and only the last 5 finished charges. History (`GET /charging-history`) is the full record of past charges and savings performance. The 5-item cap on Schedules is what keeps the two from collapsing into the same feature.
- **Planned vs. realized savings:** History reports `realizedSavingsNok` (`baselineCostNok - actualCostNok`) as the saving, and the summary sums realized savings over `COMPLETED` sessions only. The optimizer's `estimatedSavingsNok` (`baseline - optimized`) is shown for comparison but never summed. In V1 mock charging the two are equal; they stay separate concepts.
- **Charging efficiency:** V1 uses a system-level default value of `0.9`; it is not stored per EV.
- **Profile update:** `defaultPriceArea` is only a client-side default. Every price lookup and charging plan request takes an explicit price area, so a V1 user is not blocked by the value chosen at sign-up.
- **Vehicle Specification Master Data:** Obtaining comprehensive metadata may be practically difficult.
- **V1.5:** Manually build presets for a curated set of up to 30 representative vehicle models, chosen from actual Norwegian EV sales rankings.
- **Unsupported vehicles:** Continue to use manual input.
- **Full vehicle data:** Consider paid APIs or commercial data sources later if needed.
- **Automatic vehicle account integration:** Smartcar (read-only) adopted in V1.5 - see the Vehicle
  Telemetry row above. Real vehicle *control* through a manufacturer or aggregator API remains V2.

## Considered but excluded

- **Home appliances (washing machine, dishwasher) and smart home integration:** excluded from every
  version, including V2. Two reasons:
  - **Integration difficulty:** there is no single integration point. Each appliance brand and smart
    home platform has its own API, authentication, and device model, and many offer no public
    third-party API at all. Every additional vendor is a separate integration to build and maintain,
    and none of it can be verified without owning the real devices.
  - **Cost versus value:** the optimization logic (pick the cheapest window in the price series) is the
    same one WattPilot already has for EV charging, so supporting appliances adds little new
    capability. The integration and maintenance cost is high compared with that small gain. An
    appliance also uses far less energy per cycle than an EV, so the possible savings per run are small.
- **Tibber integration:** replaced by the read-only Smartcar telemetry feature above.
