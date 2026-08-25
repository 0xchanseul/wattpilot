| Feature | V1 | V1.5 | V2 | Description | Notes |
| --- | --- | --- | --- | --- | --- |
| Sign-up / Login | ✅ |  |  | Basic authentication |  |
| EV Registration | ✅ |  |  | Battery capacity, charging power | Need to verify whether integration with vehicle manufacturers is possible (Tesla, BMW, Hyundai, etc.) |
| Electricity Price Lookup | ✅ |  |  | External API | Hva koster strømmen API |
| Optimal Charging Time Calculation | ✅ |  |  | Core feature |  |
| Charging Reservation | ✅ |  |  | Scheduler |  |
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

- For Vehicle Specification Master Data, obtaining comprehensive metadata may be practically difficult.
- **V1:** Users manually enter battery capacity and actual charging power.
- **V1.5:** Manually build presets for only 10–20 representative vehicle models.
- **Unsupported vehicles:** Continue to use manual input.
- **Full vehicle data:** Consider paid APIs or commercial data sources later if needed.
- **Automatic vehicle account integration:** Review Enode, Smartcar, or manufacturer APIs in V2.
- Consider the approaches above.