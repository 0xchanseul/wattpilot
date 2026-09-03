package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.common.response.PageResponse;
import com.wattpilot.ev.dto.CreateEvRequest;
import com.wattpilot.ev.dto.EvResponse;
import com.wattpilot.ev.dto.UpdateEvRequest;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.EvStatus;
import com.wattpilot.ev.repository.EvRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns EV persistence and the ownership checks that keep one user's EVs invisible to another.
 */
@Service
@Transactional(readOnly = true)
public class EvService {

    private final EvRepository evRepository;

    public EvService(EvRepository evRepository) {
        this.evRepository = evRepository;
    }

    @Transactional
    public EvResponse register(Long userId, CreateEvRequest request) {
        Ev ev = Ev.register(
                userId,
                request.name().trim(),
                request.manufacturer().trim(),
                request.model().trim(),
                request.batteryCapacityKwh(),
                request.maxAcChargingPowerKw(),
                request.defaultChargerPowerKw());
        return EvResponse.from(evRepository.save(ev));
    }

    /**
     * Lists the caller's EVs. With no explicit filter, only ACTIVE EVs are returned so a deactivated
     * EV disappears from the default view; {@code status=INACTIVE} surfaces it again.
     */
    public PageResponse<EvResponse> list(Long userId, EvStatus statusFilter, Pageable pageable) {
        EvStatus status = statusFilter != null ? statusFilter : EvStatus.ACTIVE;
        return PageResponse.from(
                evRepository.findByUserIdAndStatus(userId, status, pageable).map(EvResponse::from));
    }

    public EvResponse get(Long userId, Long evId) {
        return EvResponse.from(getOwnedEv(userId, evId));
    }

    /**
     * Returns the caller's ACTIVE EV for use by other domains (e.g. charging optimization). A missing
     * EV, one owned by another user, and a deactivated EV are all reported as {@code EV_NOT_FOUND} so
     * a caller cannot probe an EV's existence or lifecycle state.
     */
    public Ev getActiveOwnedEv(Long userId, Long evId) {
        Ev ev = getOwnedEv(userId, evId);
        if (!ev.isActive()) {
            throw new BusinessException(ErrorCode.EV_NOT_FOUND);
        }
        return ev;
    }

    @Transactional
    public EvResponse update(Long userId, Long evId, UpdateEvRequest request) {
        Ev ev = getOwnedEv(userId, evId);
        ev.updateProfile(
                trimOrNull(request.name()),
                trimOrNull(request.manufacturer()),
                trimOrNull(request.model()),
                request.batteryCapacityKwh(),
                request.maxAcChargingPowerKw(),
                request.defaultChargerPowerKw());
        ev.changeStatus(request.status());
        // Flush so the @UpdateTimestamp is populated before the response is built.
        return EvResponse.from(evRepository.saveAndFlush(ev));
    }

    @Transactional
    public void deactivate(Long userId, Long evId) {
        getOwnedEv(userId, evId).deactivate();
    }

    private Ev getOwnedEv(Long userId, Long evId) {
        return evRepository.findByIdAndUserId(evId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EV_NOT_FOUND));
    }

    private static String trimOrNull(String value) {
        return value != null ? value.trim() : null;
    }
}
