package com.wattpilot.charging.service;

import com.wattpilot.charging.ChargingProperties;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.PricePoint;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.service.EvService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Turns a charging request into feasible charging-window candidates.
 *
 * <p>Verifies EV ownership, derives the energy target from the battery percentages, loads the stored
 * prices for the window and hands the pure arithmetic to {@link ChargingWindowCalculator}. It never
 * persists anything: the preview reads the candidates directly, and the scheduler re-runs this
 * calculation before saving the one candidate the user confirmed.
 *
 * <p>Malformed input is rejected with a {@link BusinessException}; a well-formed request that simply
 * cannot be satisfied comes back as {@link ChargingCandidatesResult.Infeasible}.
 */
@Service
@Transactional(readOnly = true)
public class ChargingOptimizationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int ENERGY_INTERNAL_SCALE = 6;

    private final EvService evService;
    private final ElectricityPriceService electricityPriceService;
    private final ChargingWindowCalculator calculator;
    private final ChargingProperties chargingProperties;
    private final Clock clock;

    public ChargingOptimizationService(EvService evService,
                                       ElectricityPriceService electricityPriceService,
                                       ChargingWindowCalculator calculator,
                                       ChargingProperties chargingProperties,
                                       Clock clock) {
        this.evService = evService;
        this.electricityPriceService = electricityPriceService;
        this.calculator = calculator;
        this.chargingProperties = chargingProperties;
        this.clock = clock;
    }

    /**
     * All feasible continuous charging windows for the request, ranked cheapest first. The optional
     * {@code ev} is used when the caller has already loaded (and possibly locked) the EV row; passing
     * {@code null} makes this method load it via the ownership check itself.
     */
    public ChargingCandidatesResult calculateCandidates(OptimizationCommand command, Ev ev) {
        Inputs inputs = resolveInputs(command, ev);
        return calculator.calculateCandidates(inputs.evSnapshot(), inputs.requiredEnergyKwh(),
                chargingProperties.efficiency(), inputs.earliestStart(), inputs.deadline(), inputs.prices());
    }

    public ChargingCandidatesResult calculateCandidates(OptimizationCommand command) {
        return calculateCandidates(command, null);
    }

    /**
     * The single cheapest window in the legacy {@link OptimizationResult} shape. Retained for the
     * existing orchestration tests; production paths use {@link #calculateCandidates}.
     */
    public OptimizationResult optimize(OptimizationCommand command) {
        Inputs inputs = resolveInputs(command, null);
        return calculator.optimize(inputs.evSnapshot(), inputs.requiredEnergyKwh(),
                chargingProperties.efficiency(), inputs.earliestStart(), inputs.deadline(), inputs.prices());
    }

    /** The effective charging window the calculator uses: {@code now} unless a later start is requested. */
    public OffsetDateTime resolveEarliestStart(OffsetDateTime requestedEarliestStartAt) {
        return earliestStart(requestedEarliestStartAt, now());
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    /** Maps an infeasible calculation onto the matching 422 error code, preserving the detail message. */
    public static BusinessException toBusinessException(ChargingCandidatesResult.Infeasible infeasible) {
        ErrorCode errorCode = switch (infeasible.reason()) {
            case DEADLINE_TOO_SOON -> ErrorCode.CHARGING_DEADLINE_TOO_SOON;
            case INSUFFICIENT_PRICE_DATA -> ErrorCode.CHARGING_PRICE_DATA_INSUFFICIENT;
            case NO_CONTINUOUS_WINDOW -> ErrorCode.CHARGING_NO_CONTINUOUS_WINDOW;
        };
        return new BusinessException(errorCode, infeasible.detail());
    }

    private Inputs resolveInputs(OptimizationCommand command, Ev preloadedEv) {
        OffsetDateTime now = now();
        validate(command, now);

        Ev ev = preloadedEv != null ? preloadedEv : evService.getActiveOwnedEv(command.userId(), command.evId());

        BigDecimal requiredEnergyKwh = ev.getBatteryCapacityKwh()
                .multiply(command.targetBatteryPercent().subtract(command.currentBatteryPercent()))
                .divide(HUNDRED, ENERGY_INTERNAL_SCALE, RoundingMode.HALF_UP);

        OffsetDateTime earliestStart = earliestStart(command.earliestStartAt(), now);
        OffsetDateTime deadline = command.requiredCompletionAt().truncatedTo(ChronoUnit.MINUTES);

        List<PricePoint> prices =
                electricityPriceService.getPricePointsInWindow(command.priceArea(), earliestStart, deadline);

        return new Inputs(EvSnapshot.from(ev), requiredEnergyKwh, earliestStart, deadline, prices);
    }

    private void validate(OptimizationCommand command, OffsetDateTime now) {
        require(command.userId() != null, "userId is required.");
        require(command.evId() != null, "evId is required.");
        require(command.priceArea() != null, "priceArea is required.");
        require(command.currentBatteryPercent() != null, "currentBatteryPercent is required.");
        require(command.targetBatteryPercent() != null, "targetBatteryPercent is required.");
        require(command.requiredCompletionAt() != null, "requiredCompletionAt is required.");

        BigDecimal current = command.currentBatteryPercent();
        BigDecimal target = command.targetBatteryPercent();
        require(current.compareTo(BigDecimal.ZERO) >= 0 && current.compareTo(HUNDRED) <= 0,
                "currentBatteryPercent must be between 0 and 100.");
        require(target.compareTo(BigDecimal.ZERO) > 0 && target.compareTo(HUNDRED) <= 0,
                "targetBatteryPercent must be greater than 0 and at most 100.");
        require(target.compareTo(current) > 0,
                "targetBatteryPercent must be greater than currentBatteryPercent.");
        require(command.requiredCompletionAt().isAfter(now),
                "requiredCompletionAt must be in the future.");
        require(command.earliestStartAt() == null
                        || command.requiredCompletionAt().isAfter(command.earliestStartAt()),
                "requiredCompletionAt must be after earliestStartAt.");
    }

    private OffsetDateTime earliestStart(OffsetDateTime requested, OffsetDateTime now) {
        if (requested == null) {
            return now;
        }
        OffsetDateTime truncated = requested.truncatedTo(ChronoUnit.MINUTES);
        return truncated.isBefore(now) ? now : truncated;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private record Inputs(EvSnapshot evSnapshot, BigDecimal requiredEnergyKwh, OffsetDateTime earliestStart,
                          OffsetDateTime deadline, List<PricePoint> prices) {
    }
}
