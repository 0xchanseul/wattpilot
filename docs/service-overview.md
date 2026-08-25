# WattPilot Service Overview

## Overview

**WattPilot** is a smart energy scheduling service that helps users charge their electric vehicles (EVs) when electricity prices are lower.

The service retrieves hourly electricity price data, calculates the energy required to reach the user's target battery level, and determines the most cost-efficient charging schedule within the available charging window.

---

## Problem

Electricity prices in Norway vary throughout the day.

EV owners can reduce charging costs by shifting charging to cheaper hours, but manually checking hourly prices and planning charging schedules is inconvenient.

WattPilot automates this process by analyzing electricity prices and creating an optimized charging schedule based on the user's EV information and charging requirements.

---

## How It Works

```
Register / Login
      ↓
Register EV
      ↓
Set charging requirements
      ↓
Retrieve electricity prices
      ↓
Calculate optimal charging time
      ↓
Create charging schedule
      ↓
Execute Mock charging
      ↓
Store charging history and savings
```

Users provide basic EV information such as battery capacity and charging speed, along with a target battery level and required completion time.

WattPilot then calculates the required charging duration and selects the most cost-efficient charging hours.

---

## MVP Scope

The first version focuses on completing the full EV charging optimization workflow.

Key features include:

- User registration and login
- EV registration
- Hourly electricity price retrieval using the **Hva koster strømmen API**
- Optimal charging time calculation
- Charging reservation and scheduling
- Mock EV charging
- Charging history
- Estimated savings calculation

Direct control of real EVs is intentionally excluded from V1. Instead, a Mock charging system is used to simulate the complete charging workflow.

---

## Future Direction

WattPilot is designed to gradually expand beyond EV charging.

**V1.5** will introduce Tibber API integration to provide more personalized electricity services. It will also include vehicle specification master data, allowing users to select their EV model and automatically retrieve information such as battery capacity and charging speed.

**V2** will introduce OAuth-based integration with supported vehicle manufacturers such as Tesla, BMW, and Hyundai. It will also expand the platform with notifications, smart home integration, and scheduling support for additional appliances such as washing machines and dishwashers.

The long-term goal is to evolve WattPilot into a broader **smart energy management platform** that automatically shifts electricity consumption to more cost-efficient periods.