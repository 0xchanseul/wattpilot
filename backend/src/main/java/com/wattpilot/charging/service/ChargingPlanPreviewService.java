package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.ChargingPlanPreviewResponse;
import com.wattpilot.charging.dto.CreateChargingPlanPreviewRequest;
import com.wattpilot.charging.dto.OptimizationCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs {@code POST /charging-plans/preview}: computes the cheapest few charging-window candidates
 * and returns them without touching the database.
 *
 * <p>All the work is delegated to {@link ChargingOptimizationService}; this service only caps the
 * candidate list and echoes the request inputs into the response.
 */
@Service
@Transactional(readOnly = true)
public class ChargingPlanPreviewService {

    /** The modal shows a small set of choices; more than three is noise. */
    private static final int MAX_CANDIDATES = 3;

    private final ChargingOptimizationService optimizationService;

    public ChargingPlanPreviewService(ChargingOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    public ChargingPlanPreviewResponse preview(Long userId, CreateChargingPlanPreviewRequest request) {
        OptimizationCommand command = new OptimizationCommand(
                userId,
                request.evId(),
                request.currentBatteryPercent(),
                request.targetBatteryPercent(),
                null,
                request.requiredCompletionAt(),
                request.priceArea());

        ChargingCandidatesResult result = optimizationService.calculateCandidates(command);
        if (result instanceof ChargingCandidatesResult.Infeasible infeasible) {
            throw ChargingOptimizationService.toBusinessException(infeasible);
        }
        ChargingCandidatesResult.Feasible feasible = (ChargingCandidatesResult.Feasible) result;

        List<ChargingCandidate> topCandidates = feasible.candidates().stream()
                .limit(MAX_CANDIDATES)
                .toList();

        return new ChargingPlanPreviewResponse(
                request.evId(),
                request.currentBatteryPercent(),
                request.targetBatteryPercent(),
                request.requiredCompletionAt(),
                request.priceArea(),
                feasible.calculatedEnergyKwh(),
                feasible.effectiveChargingPowerKw(),
                feasible.estimatedDurationMinutes(),
                topCandidates);
    }
}
