package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.ChargingScheduleResponse;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Confirms a previewed charging candidate: re-runs the calculation against the latest prices, matches
 * the user's pick, checks for conflicts, and persists the plan, its slots and one schedule in a
 * single transaction.
 *
 * <p>The client is trusted only for the original conditions and the selected start/end instants;
 * every stored figure is recomputed here. The EV row is locked for the duration so two concurrent
 * confirmations for the same EV cannot both pass the overlap check.
 */
@Service
public class ChargingScheduleService {

    /** Schedule states that still reserve the EV's time and therefore block an overlapping schedule. */
    private static final Set<ChargingScheduleStatus> ACTIVE_STATUSES = Set.of(
            ChargingScheduleStatus.CREATED,
            ChargingScheduleStatus.WAITING,
            ChargingScheduleStatus.IN_PROGRESS);

    private final ChargingOptimizationService optimizationService;
    private final ChargingCandidateSelector candidateSelector;
    private final EvService evService;
    private final ElectricityPriceService electricityPriceService;
    private final ChargingPlanRepository planRepository;
    private final ChargingPlanSlotRepository slotRepository;
    private final ChargingScheduleRepository scheduleRepository;

    public ChargingScheduleService(ChargingOptimizationService optimizationService,
                                   ChargingCandidateSelector candidateSelector,
                                   EvService evService,
                                   ElectricityPriceService electricityPriceService,
                                   ChargingPlanRepository planRepository,
                                   ChargingPlanSlotRepository slotRepository,
                                   ChargingScheduleRepository scheduleRepository) {
        this.optimizationService = optimizationService;
        this.candidateSelector = candidateSelector;
        this.evService = evService;
        this.electricityPriceService = electricityPriceService;
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public ChargingScheduleResponse createSchedule(Long userId, CreateChargingScheduleRequest request) {
        OptimizationCommand command = new OptimizationCommand(
                userId,
                request.evId(),
                request.currentBatteryPercent(),
                request.targetBatteryPercent(),
                null,
                request.requiredCompletionAt(),
                request.priceArea());

        // Lock the EV row: serialises concurrent scheduling for this EV. 404 if not owned or not ACTIVE.
        Ev ev = evService.getActiveOwnedEvForUpdate(userId, request.evId());

        // Re-validate the request (400) and recompute every candidate from the latest prices.
        ChargingCandidatesResult result = optimizationService.calculateCandidates(command, ev);
        if (result instanceof ChargingCandidatesResult.Infeasible infeasible) {
            throw ChargingOptimizationService.toBusinessException(infeasible);
        }
        ChargingCandidatesResult.Feasible feasible = (ChargingCandidatesResult.Feasible) result;

        // 409 if the user's pick is no longer a current candidate (prices moved / start has passed).
        ChargingCandidate selected =
                candidateSelector.select(feasible.candidates(), request.selectedStartAt(), request.selectedEndAt());

        requireNoOverlap(userId, request.evId(), selected);

        ChargingPlan plan = planRepository.save(ChargingPlan.succeeded(
                userId, request.evId(), request.priceArea(),
                request.currentBatteryPercent(), request.targetBatteryPercent(),
                optimizationService.resolveEarliestStart(null), request.requiredCompletionAt(),
                EvSnapshot.from(ev),
                feasible.calculatedEnergyKwh(), feasible.effectiveChargingPowerKw(),
                feasible.estimatedDurationMinutes(), selected));

        List<ElectricityPrice> pricesInWindow = electricityPriceService.getPricesInWindow(
                request.priceArea(), selected.recommendedStartAt(), selected.recommendedEndAt());
        List<ChargingPlanSlot> slotEntities =
                ChargingSlotMapper.toEntities(plan.getId(), selected.slots(), pricesInWindow);
        slotRepository.saveAll(slotEntities);

        ChargingSchedule schedule = scheduleRepository.save(ChargingSchedule.create(
                plan.getId(),
                selected.recommendedStartAt(),
                selected.recommendedEndAt(),
                selected.expectedEnergyKwh(),
                selected.estimatedCostNok()));

        return ChargingScheduleResponse.of(schedule, plan, ChargingSlotMapper.toDtos(slotEntities));
    }

    @Transactional(readOnly = true)
    public ChargingScheduleResponse getSchedule(Long userId, Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));
        ChargingPlan plan = planRepository.findById(schedule.getChargingPlanId())
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));
        return ChargingScheduleResponse.of(schedule, plan, ChargingSlotMapper.toDtos(
                slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChargingScheduleResponse> listSchedules(Long userId, Pageable pageable) {
        List<Long> planIds = planRepository.findIdsByUserId(userId);
        if (planIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        Page<ChargingSchedule> page = scheduleRepository.findByChargingPlanIdIn(planIds, pageable);
        List<Long> pagePlanIds = page.getContent().stream().map(ChargingSchedule::getChargingPlanId).toList();

        Map<Long, ChargingPlan> plansById = planRepository.findAllById(pagePlanIds).stream()
                .collect(Collectors.toMap(ChargingPlan::getId, Function.identity()));
        Map<Long, List<ChargingPlanSlot>> slotsByPlan = slotRepository
                .findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(pagePlanIds).stream()
                .collect(Collectors.groupingBy(ChargingPlanSlot::getChargingPlanId));

        return PageResponse.from(page.map(schedule -> ChargingScheduleResponse.of(
                schedule,
                plansById.get(schedule.getChargingPlanId()),
                ChargingSlotMapper.toDtos(slotsByPlan.getOrDefault(schedule.getChargingPlanId(), List.of())))));
    }

    private void requireNoOverlap(Long userId, Long evId, ChargingCandidate selected) {
        List<Long> evPlanIds = planRepository.findIdsByUserIdAndEvId(userId, evId);
        if (!evPlanIds.isEmpty() && scheduleRepository.existsActiveOverlap(
                evPlanIds, ACTIVE_STATUSES, selected.recommendedStartAt(), selected.recommendedEndAt())) {
            throw new BusinessException(ErrorCode.CHARGING_SCHEDULE_CONFLICT,
                    "This EV already has an active charging schedule between %s and %s."
                            .formatted(selected.recommendedStartAt(), selected.recommendedEndAt()));
        }
    }
}
