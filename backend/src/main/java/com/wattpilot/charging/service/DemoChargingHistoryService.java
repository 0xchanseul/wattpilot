package com.wattpilot.charging.service;

import com.wattpilot.charging.repository.DemoChargingHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Gives a demo visitor a copy of the template account's finished charging history, so the history,
 * dashboard and savings screens are not empty on a first visit. Only finished charges are copied; see
 * {@link DemoChargingHistoryRepository#copyFinishedHistory}.
 */
@Service
public class DemoChargingHistoryService {

    private final DemoChargingHistoryRepository repository;

    public DemoChargingHistoryService(DemoChargingHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * @param visitorEvIdByTemplateEvId the visitor's copy of each template EV, keyed by the template
     *                                  EV's id; history follows the EV it was recorded for
     */
    @Transactional
    public void copyFinishedHistory(Long templateUserId, Long visitorUserId,
                                    Map<Long, Long> visitorEvIdByTemplateEvId) {
        visitorEvIdByTemplateEvId.forEach((templateEvId, visitorEvId) ->
                repository.copyFinishedHistory(templateUserId, templateEvId, visitorUserId, visitorEvId));
    }
}
