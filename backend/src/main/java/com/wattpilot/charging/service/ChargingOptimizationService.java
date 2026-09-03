package com.wattpilot.charging.service;

import com.wattpilot.charging.ChargingProperties;
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
 * Turns a charging request into a recommended continuous charging window.
 *
 * <p>Verifies EV ownership, derives the energy target from the battery percentages, loads the stored
 * prices for the window and hands the pure arithmetic to {@link ChargingWindowCalculator}.
 *
 * <p>Current V1 step: the result is returned only, never persisted. Malformed input is rejected with
 * a {@link BusinessException}; a well-formed request that simply cannot be satisfied comes back as
 * {@link OptimizationResult.Infeasible}.
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

    public OptimizationResult optimize(OptimizationCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        validate(command, now);

        Ev ev = evService.getActiveOwnedEv(command.userId(), command.evId());

        BigDecimal requiredEnergyKwh = ev.getBatteryCapacityKwh()
                .multiply(command.targetBatteryPercent().subtract(command.currentBatteryPercent()))
                .divide(HUNDRED, ENERGY_INTERNAL_SCALE, RoundingMode.HALF_UP);

        OffsetDateTime earliestStart = earliestStart(command.earliestStartAt(), now);
        OffsetDateTime deadline = command.requiredCompletionAt().truncatedTo(ChronoUnit.MINUTES);

        List<PricePoint> prices =
                electricityPriceService.getPricePointsInWindow(command.priceArea(), earliestStart, deadline);

        return calculator.optimize(EvSnapshot.from(ev), requiredEnergyKwh,
                chargingProperties.efficiency(), earliestStart, deadline, prices);
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
}
