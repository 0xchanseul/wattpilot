package com.wattpilot.history.service;

import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanSlot;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.charging.service.ChargingSlotMapper;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.response.PageMetadata;
import com.wattpilot.history.dto.ChargingHistoryDetail;
import com.wattpilot.history.dto.ChargingHistoryItem;
import com.wattpilot.history.dto.ChargingHistoryListResponse;
import com.wattpilot.history.dto.ChargingHistorySummary;
import com.wattpilot.history.repository.ChargingHistoryRepository;
import com.wattpilot.history.repository.ChargingHistoryRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read access to executed charging sessions for the charging-history endpoints.
 *
 * <p>History covers terminal executions only: {@link ChargingSessionStatus#COMPLETED} and
 * {@link ChargingSessionStatus#FAILED}. A schedule still WAITING, one currently charging (session
 * {@code STARTED}), and a reservation cancelled before it ever ran are deliberately excluded — they
 * are not charging that happened, and the Schedules overview shows them instead.
 *
 * <p>Savings are always realized figures here ({@code baselineCostNok - actualCostNok}), never the
 * plan estimate. The list header summary and the per-item {@code realizedSavingsNok} both follow that
 * rule; the plan's own {@code estimatedSavingsNok} is shown alongside for comparison but never summed.
 */
@Service
@Transactional(readOnly = true)
public class ChargingHistoryService {

    static final List<ChargingSessionStatus> HISTORY_STATUSES =
            List.of(ChargingSessionStatus.COMPLETED, ChargingSessionStatus.FAILED);

    private final ChargingHistoryRepository historyRepository;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingScheduleRepository scheduleRepository;
    private final ChargingPlanRepository planRepository;
    private final ChargingPlanSlotRepository slotRepository;

    public ChargingHistoryService(ChargingHistoryRepository historyRepository,
                                  ChargingSessionRepository sessionRepository,
                                  ChargingScheduleRepository scheduleRepository,
                                  ChargingPlanRepository planRepository,
                                  ChargingPlanSlotRepository slotRepository) {
        this.historyRepository = historyRepository;
        this.sessionRepository = sessionRepository;
        this.scheduleRepository = scheduleRepository;
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
    }

    public ChargingHistoryListResponse listHistory(Long userId, Long evId,
                                                   ChargingSessionStatus statusFilter, Pageable pageable) {
        ChargingHistorySummary summary = ChargingHistorySummary.from(evId != null
                ? historyRepository.summarizeByEv(userId, evId, HISTORY_STATUSES, ChargingSessionStatus.COMPLETED)
                : historyRepository.summarize(userId, HISTORY_STATUSES, ChargingSessionStatus.COMPLETED));

        List<ChargingSessionStatus> statuses = resolveStatuses(statusFilter);
        if (statuses.isEmpty()) {
            return new ChargingHistoryListResponse(summary, List.of(), PageMetadata.from(Page.empty(pageable)));
        }

        // The list order is fixed by the repository query; drop any client-supplied sort.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<ChargingHistoryRow> page = evId != null
                ? historyRepository.findHistoryByEv(userId, evId, statuses, unsorted)
                : historyRepository.findHistory(userId, statuses, unsorted);

        return new ChargingHistoryListResponse(
                summary,
                page.map(ChargingHistoryItem::from).getContent(),
                PageMetadata.from(page));
    }

    public ChargingHistoryDetail getHistoryDetail(Long userId, Long sessionId) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .filter(candidate -> HISTORY_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_HISTORY_NOT_FOUND));
        ChargingSchedule schedule = scheduleRepository.findById(session.getChargingScheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_HISTORY_NOT_FOUND));
        ChargingPlan plan = planRepository.findById(schedule.getChargingPlanId())
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_HISTORY_NOT_FOUND));

        List<ChargingPlanSlot> slots = slotRepository.findByChargingPlanIdOrderBySequenceNoAsc(plan.getId());
        return ChargingHistoryDetail.of(session, schedule, plan, ChargingSlotMapper.toDtos(slots));
    }

    /**
     * The status filter can only narrow the history set. A value outside {@link #HISTORY_STATUSES}
     * (e.g. {@code STARTED}) matches nothing, mirroring how the charging-plan list treats
     * {@code status=FAILED}.
     */
    private static List<ChargingSessionStatus> resolveStatuses(ChargingSessionStatus statusFilter) {
        if (statusFilter == null) {
            return HISTORY_STATUSES;
        }
        return HISTORY_STATUSES.contains(statusFilter) ? List.of(statusFilter) : List.of();
    }
}
