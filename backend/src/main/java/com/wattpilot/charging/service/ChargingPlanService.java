package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingPlanResponse;
import com.wattpilot.charging.dto.CreateChargingPlanRequest;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.dto.OptimizationResult;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.exception.ChargingPlanInfeasibleException;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.response.PageResponse;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.service.EvService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persists a charging-plan optimization attempt and shapes the HTTP result.
 *
 * <p>Delegates the calculation to {@link ChargingOptimizationService} (ownership check, energy
 * derivation, price lookup, pure window math), then stores the attempt as a {@code charging_plans}
 * row. A feasible result is saved as SUCCEEDED with its slots and returned; an infeasible result is
 * saved as FAILED and surfaced as a 422 via {@link ChargingPlanInfeasibleException}.
 */
@Service
public class ChargingPlanService {

    private final ChargingOptimizationService optimizationService;
    private final EvService evService;
    private final ElectricityPriceService electricityPriceService;
    private final ChargingPlanRepository planRepository;
    private final ChargingPlanSlotRepository slotRepository;
    private final Clock clock;

    public ChargingPlanService(ChargingOptimizationService optimizationService,
                               EvService evService,
                               ElectricityPriceService electricityPriceService,
                               ChargingPlanRepository planRepository,
                               ChargingPlanSlotRepository slotRepository,
                               Clock clock) {
        this.optimizationService = optimizationService;
        this.evService = evService;
        this.electricityPriceService = electricityPriceService;
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    /**
     * Calculates and persists a charging plan for the caller.
     *
     * <p>The FAILED branch deliberately commits before the 422 is raised, so
     * {@link ChargingPlanInfeasibleException} is excluded from rollback. Every other failure (a slot
     * insert violating a constraint, for instance) rolls the whole attempt back.
     */
    @Transactional(noRollbackFor = ChargingPlanInfeasibleException.class)
    public ChargingPlanResponse createPlan(Long userId, CreateChargingPlanRequest request) {
        OptimizationCommand command = new OptimizationCommand(
                userId,
                request.evId(),
                request.currentBatteryPercent(),
                request.targetBatteryPercent(),
                request.earliestStartAt(),
                request.requiredCompletionAt(),
                request.priceArea());

        // Validates the request (400) and the EV ownership/ACTIVE state (404) before anything is stored.
        OptimizationResult result = optimizationService.optimize(command);

        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime resolvedEarliestStart = resolveEarliestStart(request.earliestStartAt(), now);

        if (result instanceof OptimizationResult.Success success) {
            ChargingPlan plan = planRepository.save(ChargingPlan.succeeded(
                    userId, request.evId(), request.priceArea(),
                    request.currentBatteryPercent(), request.targetBatteryPercent(),
                    resolvedEarliestStart, request.requiredCompletionAt(), success));

            slotRepository.saveAll(toSlotEntities(plan.getId(), request.priceArea(), success));
            return ChargingPlanResponse.of(plan, success.slots());
        }

        OptimizationResult.Infeasible infeasible = (OptimizationResult.Infeasible) result;
        // optimize() already passed the ownership/ACTIVE check, so this lookup cannot fail here.
        Ev ev = evService.getActiveOwnedEv(userId, request.evId());
        ChargingPlan failed = planRepository.save(ChargingPlan.failed(
                userId, request.evId(), request.priceArea(),
                request.currentBatteryPercent(), request.targetBatteryPercent(),
                resolvedEarliestStart, request.requiredCompletionAt(),
                EvSnapshot.from(ev), infeasible.detail()));

        throw new ChargingPlanInfeasibleException(failed.getId(), infeasible.reason(), infeasible.detail());
    }

    @Transactional(readOnly = true)
    public ChargingPlanResponse getPlan(Long userId, Long planId) {
        ChargingPlan plan = planRepository
                .findByIdAndUserIdAndStatus(planId, userId, ChargingPlanStatus.SUCCEEDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_PLAN_NOT_FOUND));
        return ChargingPlanResponse.of(plan, toSlotDtos(
                slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChargingPlanResponse> listPlans(Long userId, Long evId, ChargingPlanStatus statusFilter,
                                                        Pageable pageable) {
        // Only SUCCEEDED plans are exposed; a status=FAILED filter therefore returns an empty page.
        if (statusFilter == ChargingPlanStatus.FAILED) {
            return PageResponse.from(Page.empty(pageable));
        }

        Page<ChargingPlan> page = evId != null
                ? planRepository.findByUserIdAndEvIdAndStatus(userId, evId, ChargingPlanStatus.SUCCEEDED, pageable)
                : planRepository.findByUserIdAndStatus(userId, ChargingPlanStatus.SUCCEEDED, pageable);

        Map<Long, List<ChargingPlanSlot>> slotsByPlan = slotRepository
                .findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(
                        page.getContent().stream().map(ChargingPlan::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ChargingPlanSlot::getChargingPlanId));

        return PageResponse.from(page.map(plan -> ChargingPlanResponse.of(
                plan, toSlotDtos(slotsByPlan.getOrDefault(plan.getId(), List.of())))));
    }

    /**
     * Links each recommended slot to the {@code electricity_prices} row it was priced from, matching
     * by instant containment (a slot lies entirely within exactly one stored hour), and copies the
     * calculator's already-rounded energy and cost verbatim so the persisted slot sums stay equal to
     * the plan's {@code expectedEnergyKwh} and {@code estimatedCostNok}.
     */
    private List<ChargingPlanSlot> toSlotEntities(Long planId, PriceArea priceArea,
                                                  OptimizationResult.Success success) {
        List<ElectricityPrice> prices = electricityPriceService.getPricesInWindow(
                priceArea, success.recommendedStartAt(), success.recommendedEndAt());

        List<ChargingPlanSlot> entities = new ArrayList<>(success.slots().size());
        int sequenceNo = 1;
        for (com.wattpilot.charging.dto.ChargingPlanSlot slot : success.slots()) {
            ElectricityPrice price = priceCovering(prices, slot.startsAt(), slot.endsAt());
            entities.add(ChargingPlanSlot.of(
                    planId,
                    price.getId(),
                    slot.startsAt(),
                    slot.endsAt(),
                    slot.pricePerKwh(),
                    slot.plannedEnergyKwh(),
                    slot.expectedCostNok(),
                    sequenceNo++));
        }
        return entities;
    }

    private static ElectricityPrice priceCovering(List<ElectricityPrice> prices, OffsetDateTime slotStart,
                                                  OffsetDateTime slotEnd) {
        Instant start = slotStart.toInstant();
        Instant end = slotEnd.toInstant();
        return prices.stream()
                .filter(price -> !price.getStartsAt().toInstant().isAfter(start)
                        && !price.getEndsAt().toInstant().isBefore(end))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No stored electricity price covers charging slot [%s, %s]".formatted(slotStart, slotEnd)));
    }

    private static List<com.wattpilot.charging.dto.ChargingPlanSlot> toSlotDtos(List<ChargingPlanSlot> slots) {
        return slots.stream()
                .map(slot -> new com.wattpilot.charging.dto.ChargingPlanSlot(
                        slot.getSlotStartAt().atZoneSameInstant(ElectricityPriceService.PRICE_ZONE).toOffsetDateTime(),
                        slot.getSlotEndAt().atZoneSameInstant(ElectricityPriceService.PRICE_ZONE).toOffsetDateTime(),
                        slot.getPricePerKwh(),
                        slot.getPlannedEnergyKwh(),
                        slot.getExpectedCostNok()))
                .toList();
    }

    /**
     * Same resolution {@link ChargingOptimizationService} applies internally: an absent or past
     * earliest start becomes "now", otherwise it is minute-aligned. Kept here so the stored
     * {@code earliest_start_at} is exactly the window start the optimizer used.
     */
    private static OffsetDateTime resolveEarliestStart(OffsetDateTime requested, OffsetDateTime now) {
        if (requested == null) {
            return now;
        }
        OffsetDateTime truncated = requested.truncatedTo(ChronoUnit.MINUTES);
        return truncated.isBefore(now) ? now : truncated;
    }
}
