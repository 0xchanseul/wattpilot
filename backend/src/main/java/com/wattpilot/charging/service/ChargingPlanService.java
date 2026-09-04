package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingPlanResponse;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read access to persisted charging plans.
 *
 * <p>Plans are created only as part of confirming a schedule (see {@code ChargingScheduleService}); a
 * bare preview never persists one. Every stored plan is SUCCEEDED, so the {@code status} filter on the
 * list endpoint only ever narrows an already-SUCCEEDED set.
 */
@Service
@Transactional(readOnly = true)
public class ChargingPlanService {

    private final ChargingPlanRepository planRepository;
    private final ChargingPlanSlotRepository slotRepository;

    public ChargingPlanService(ChargingPlanRepository planRepository, ChargingPlanSlotRepository slotRepository) {
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
    }

    public ChargingPlanResponse getPlan(Long userId, Long planId) {
        ChargingPlan plan = planRepository
                .findByIdAndUserIdAndStatus(planId, userId, ChargingPlanStatus.SUCCEEDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_PLAN_NOT_FOUND));
        return ChargingPlanResponse.of(plan, ChargingSlotMapper.toDtos(
                slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId())));
    }

    public PageResponse<ChargingPlanResponse> listPlans(Long userId, Long evId, ChargingPlanStatus statusFilter,
                                                        Pageable pageable) {
        // Only SUCCEEDED plans exist/are exposed; a status=FAILED filter therefore returns an empty page.
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
                plan, ChargingSlotMapper.toDtos(slotsByPlan.getOrDefault(plan.getId(), List.of())))));
    }
}
