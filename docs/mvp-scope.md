| Feature | V1 | V1.5 | V2 | Description | Notes |
| --- | --- | --- | --- | --- | --- |
| Sign-up / Login | ✅ |  |  | Basic authentication |  |
| Profile Update |  | ✅ |  | Update name and default price area | Not part of the V1 user flow |
| EV Registration | ✅ |  |  | Battery capacity, maximum AC charging power, default charger power | Manual input in V1 |
| Electricity Price Lookup | ✅ |  |  | External API | Hva koster strømmen API |
| Optimal Charging Time Calculation | ✅ |  |  | Core feature | Continuous charging window only; preview returns up to 3 candidates and persists nothing |
| Charging Reservation | ✅ |  |  | Scheduler | The user confirms one previewed candidate; only that candidate is stored as plan + slots + schedule |
| Actual EV Control | ❌ |  |  | Use Mock |  |
| Mock Charging | ✅ |  |  | Used instead of actual devices |  |
| Charging History | ✅ |  |  |  |  |
| Savings Calculation | ✅ |  |  |  |  |
| Tibber API |  | ✅ |  |  | Support personalization through Tibber API integration |
| Vehicle Specification Master Data |  | ✅ |  | Build master data for vehicle specifications | Automatically display vehicle specifications when the user selects only the vehicle model |
| Manufacturer Integration |  |  | ✅ | Vehicle manufacturer integration | Support integration with vehicle manufacturers through OAuth |
| Notifications |  |  | ✅ |  |  |
| Washing Machine |  |  | ✅ |  |  |
| Dishwasher |  |  | ✅ |  |  |
| Smart Home Integration |  |  | ✅ |  |  |

- **V1 EV input:** Users manually enter battery capacity, maximum AC charging power, and default charger power.
- **V1 charging optimization:** Only continuous charging windows are supported. The selected price slots must be consecutive.
- **Preview vs. confirm:** `POST /charging-plans/preview` calculates up to three cheapest candidates and writes nothing to the database. `POST /charging-schedules` re-runs the calculation against the latest prices, keeps only the candidate the user selected, and stores it as one `charging_plans` row, its `charging_plan_slots`, and one `charging_schedules` row in a single transaction. Unselected candidates and infeasible previews are never persisted.
- **Server is authoritative:** the confirm request sends only the original conditions and the selected start/end instants — never a cost, energy or slot list. Every stored figure is recomputed by the server.
- **Charging plan vs. reservation:** A charging plan is the optimizer's recommendation for the confirmed candidate. A charging schedule (reservation) is its execution booking, created together with the plan.
- **Charging efficiency:** V1 uses a system-level default value of `0.9`; it is not stored per EV.
- **Profile update:** `defaultPriceArea` is only a client-side default. Every price lookup and charging plan request takes an explicit price area, so a V1 user is not blocked by the value chosen at sign-up.
- **Vehicle Specification Master Data:** Obtaining comprehensive metadata may be practically difficult.
- **V1.5:** Manually build presets for only 10–20 representative vehicle models.
- **Unsupported vehicles:** Continue to use manual input.
- **Full vehicle data:** Consider paid APIs or commercial data sources later if needed.
- **Automatic vehicle account integration:** Review Enode, Smartcar, or manufacturer APIs in V2.
