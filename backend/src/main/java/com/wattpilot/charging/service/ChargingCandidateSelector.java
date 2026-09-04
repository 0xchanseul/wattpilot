package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Matches the window a user picked from a preview against a freshly recomputed candidate list.
 *
 * <p>The client only sends back the selected start and end instants — never the previewed cost or
 * energy. If prices moved (or the start has since passed) so that no fresh candidate has that exact
 * window, the pick is rejected with 409 and the client must request a new preview.
 */
@Component
public class ChargingCandidateSelector {

    public ChargingCandidate select(List<ChargingCandidate> freshCandidates, OffsetDateTime selectedStartAt,
                                    OffsetDateTime selectedEndAt) {
        Instant start = selectedStartAt.toInstant();
        Instant end = selectedEndAt.toInstant();
        return freshCandidates.stream()
                .filter(candidate -> candidate.recommendedStartAt().toInstant().equals(start)
                        && candidate.recommendedEndAt().toInstant().equals(end))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGING_CANDIDATE_UNAVAILABLE,
                        "The selected charging window (%s to %s) is not among the current recommendations."
                                .formatted(selectedStartAt, selectedEndAt)));
    }
}
