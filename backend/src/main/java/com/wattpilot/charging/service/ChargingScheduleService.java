package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.charging.dto.ChargingCandidatesResult;
import com.wattpilot.charging.dto.ChargingScheduleRecentActivity;
import com.wattpilot.charging.dto.ChargingScheduleResponse;
import com.wattpilot.charging.dto.ChargingSchedulesOverviewResponse;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.dto.EvSnapshot;
import com.wattpilot.charging.dto.OptimizationCommand;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.PricePoint;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.service.EvService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            ChargingScheduleStatus.WAITING,
            ChargingScheduleStatus.IN_PROGRESS);

    /** {@code recentActivity} statuses for the Schedules overview. */
    private static final Set<ChargingScheduleStatus> TERMINAL_ACTIVITY_STATUSES = Set.of(
            ChargingScheduleStatus.COMPLETED,
            ChargingScheduleStatus.FAILED);

    /**
     * How many finished charges the Schedules overview keeps in {@code recentActivity}. This is a role
     * boundary between Schedules (current/near-future operations) and the charging-history endpoints
     * (the full record of past charges), not a performance limit — see
     * {@link ChargingSchedulesOverviewResponse}.
     */
    public static final int RECENT_ACTIVITY_LIMIT = 5;

    /** How long after its start a previewed window may still be confirmed (shortened to start now). */
    public static final Duration START_DELAY_GRACE = Duration.ofMinutes(5);

    private final ChargingOptimizationService optimizationService;
    private final ChargingCandidateSelector candidateSelector;
    private final ChargingWindowCalculator windowCalculator;
    private final EvService evService;
    private final ElectricityPriceService electricityPriceService;
    private final ChargingPlanRepository planRepository;
    private final ChargingPlanSlotRepository slotRepository;
    private final ChargingScheduleRepository scheduleRepository;
    private final ChargingSessionRepository sessionRepository;

    public ChargingScheduleService(ChargingOptimizationService optimizationService,
                                   ChargingCandidateSelector candidateSelector,
                                   ChargingWindowCalculator windowCalculator,
                                   EvService evService,
                                   ElectricityPriceService electricityPriceService,
                                   ChargingPlanRepository planRepository,
                                   ChargingPlanSlotRepository slotRepository,
                                   ChargingScheduleRepository scheduleRepository,
                                   ChargingSessionRepository sessionRepository) {
        this.optimizationService = optimizationService;
        this.candidateSelector = candidateSelector;
        this.windowCalculator = windowCalculator;
        this.evService = evService;
        this.electricityPriceService = electricityPriceService;
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Whether a charge is running right now for any of the user's EVs. */
    @Transactional(readOnly = true)
    public boolean hasInProgressSchedule(Long userId) {
        return scheduleRepository.existsForUserWithStatus(userId, ChargingScheduleStatus.IN_PROGRESS);
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
        ChargingCandidate selected = selectCandidate(request, ev, feasible);

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

        return ChargingScheduleResponse.of(schedule, plan, ChargingSlotMapper.toDtos(slotEntities), null);
    }

    /**
     * A previewed window that starts "now" is already in the past by the time the user confirms, so it
     * can never match a fresh candidate. When the start has slipped by no more than
     * {@link #START_DELAY_GRACE} the window is shortened to begin now and keeps its end; anything
     * older goes through the strict match and is rejected as unavailable.
     */
    private ChargingCandidate selectCandidate(CreateChargingScheduleRequest request, Ev ev,
                                              ChargingCandidatesResult.Feasible feasible) {
        OffsetDateTime now = optimizationService.now();
        if (!isWithinStartGrace(request, feasible.estimatedDurationMinutes(), now)) {
            return candidateSelector.select(feasible.candidates(), request.selectedStartAt(), request.selectedEndAt());
        }

        List<PricePoint> prices =
                electricityPriceService.getPricePointsInWindow(request.priceArea(), now, request.selectedEndAt());
        ChargingCandidate trimmed = windowCalculator.trimStart(
                feasible.candidates().get(0), EvSnapshot.from(ev), now, request.selectedEndAt(), prices);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.CHARGING_CANDIDATE_UNAVAILABLE,
                    "Stored prices no longer cover the selected charging window.");
        }
        return trimmed;
    }

    /**
     * True when the picked start passed at most {@link #START_DELAY_GRACE} ago and the window is still a
     * full-length one that finishes in the future and by the deadline, so the shortened window cannot be
     * something the user never saw.
     */
    private static boolean isWithinStartGrace(CreateChargingScheduleRequest request, int durationMinutes,
                                              OffsetDateTime now) {
        OffsetDateTime start = request.selectedStartAt();
        OffsetDateTime end = request.selectedEndAt();
        return start.isBefore(now)
                && !start.isBefore(now.minus(START_DELAY_GRACE))
                && end.isAfter(now)
                && !end.isAfter(request.requiredCompletionAt())
                && Duration.between(start, end).toMinutes() == durationMinutes;
    }

    @Transactional(readOnly = true)
    public ChargingScheduleResponse getSchedule(Long userId, Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));
        ChargingPlan plan = planRepository.findById(schedule.getChargingPlanId())
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));
        ChargingSession session = sessionRepository.findByChargingScheduleId(schedule.getId()).orElse(null);
        return ChargingScheduleResponse.of(schedule, plan, ChargingSlotMapper.toDtos(
                slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId())), session);
    }

    /**
     * The Schedules screen payload: {@code upcoming} + {@code inProgress} (full schedule views) and a
     * short {@code recentActivity} tail of the last {@link #RECENT_ACTIVITY_LIMIT} finished charges.
     * Finished charges are not accumulated here — that is the charging-history endpoints' role.
     */
    @Transactional(readOnly = true)
    public ChargingSchedulesOverviewResponse getSchedulesOverview(Long userId) {
        List<Long> planIds = planRepository.findIdsByUserId(userId);
        if (planIds.isEmpty()) {
            return ChargingSchedulesOverviewResponse.empty();
        }

        List<ChargingSchedule> active = scheduleRepository
                .findByPlanIdsAndStatusInOrderByScheduledStartAt(planIds, ACTIVE_STATUSES);
        List<ChargingSchedule> recent = scheduleRepository
                .findRecentActivity(planIds, TERMINAL_ACTIVITY_STATUSES, Limit.of(RECENT_ACTIVITY_LIMIT));

        List<ChargingSchedule> all = Stream.concat(active.stream(), recent.stream()).toList();
        List<Long> allPlanIds = all.stream().map(ChargingSchedule::getChargingPlanId).distinct().toList();
        List<Long> allScheduleIds = all.stream().map(ChargingSchedule::getId).toList();

        Map<Long, ChargingPlan> plansById = planRepository.findAllById(allPlanIds).stream()
                .collect(Collectors.toMap(ChargingPlan::getId, Function.identity()));
        Map<Long, List<ChargingPlanSlot>> slotsByPlan = slotRepository
                .findByChargingPlanIdInOrderByChargingPlanIdAscSequenceNoAsc(
                        active.stream().map(ChargingSchedule::getChargingPlanId).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(ChargingPlanSlot::getChargingPlanId));
        Map<Long, ChargingSession> sessionsBySchedule = sessionRepository.findByChargingScheduleIdIn(allScheduleIds)
                .stream()
                .collect(Collectors.toMap(ChargingSession::getChargingScheduleId, Function.identity()));

        List<ChargingScheduleResponse> activeViews = active.stream()
                .map(schedule -> ChargingScheduleResponse.of(
                        schedule,
                        plansById.get(schedule.getChargingPlanId()),
                        ChargingSlotMapper.toDtos(slotsByPlan.getOrDefault(schedule.getChargingPlanId(), List.of())),
                        sessionsBySchedule.get(schedule.getId())))
                .toList();

        List<ChargingScheduleRecentActivity> recentActivity = recent.stream()
                .map(schedule -> ChargingScheduleRecentActivity.of(
                        schedule,
                        plansById.get(schedule.getChargingPlanId()),
                        sessionsBySchedule.get(schedule.getId())))
                .toList();

        return new ChargingSchedulesOverviewResponse(
                activeViews.stream().filter(view -> view.status() == ChargingScheduleStatus.WAITING).toList(),
                activeViews.stream().filter(view -> view.status() == ChargingScheduleStatus.IN_PROGRESS).toList(),
                recentActivity);
    }

    /** Cancels a schedule that has not started yet. Any other status is reported as a 409 conflict. */
    @Transactional
    public ChargingScheduleResponse cancelSchedule(Long userId, Long scheduleId) {
        ChargingSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));
        ChargingPlan plan = planRepository.findById(schedule.getChargingPlanId())
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_FOUND));

        if (schedule.getStatus() != ChargingScheduleStatus.WAITING) {
            throw new BusinessException(ErrorCode.CHARGING_SCHEDULE_NOT_CANCELLABLE);
        }
        schedule.markCancelled();

        return ChargingScheduleResponse.of(schedule, plan, ChargingSlotMapper.toDtos(
                slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId())), null);
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
