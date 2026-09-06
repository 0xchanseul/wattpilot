package com.wattpilot.charging.service;

import com.wattpilot.charging.entity.ChargingPlan;

import java.math.BigDecimal;

/**
 * Derives a completed session's outcome from the plan snapshot taken at confirmation time. V1 mock
 * charging always finishes exactly as planned (no partial-charge simulation), so the "actual" figures
 * are the plan's grid-side energy and cost; baseline/optimized/savings are the plan's own recommendation
 * figures, carried forward unchanged. Electricity prices and the plan are never re-read here.
 */
final class ChargingResultCalculator {

    private ChargingResultCalculator() {
    }

    static Result fromPlanSnapshot(ChargingPlan plan) {
        return new Result(
                plan.getExpectedEnergyKwh(),
                plan.getEstimatedCostNok(),
                plan.getBaselineCostNok(),
                plan.getEstimatedCostNok(),
                plan.getExpectedSavingsNok());
    }

    record Result(BigDecimal actualEnergyKwh, BigDecimal actualCostNok, BigDecimal baselineCostNok,
                  BigDecimal optimizedCostNok, BigDecimal estimatedSavingsNok) {
    }
}
