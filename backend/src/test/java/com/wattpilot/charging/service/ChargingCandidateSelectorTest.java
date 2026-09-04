package com.wattpilot.charging.service;

import com.wattpilot.charging.dto.ChargingCandidate;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargingCandidateSelectorTest {

    private final ChargingCandidateSelector selector = new ChargingCandidateSelector();

    @Test
    void returnsTheCandidateWhoseWindowMatchesTheSelectedInstants() {
        ChargingCandidate first = candidate(1, "2026-09-04T01:00:00+02:00", "2026-09-04T04:00:00+02:00");
        ChargingCandidate second = candidate(2, "2026-09-04T00:00:00+02:00", "2026-09-04T03:00:00+02:00");

        ChargingCandidate selected = selector.select(List.of(first, second),
                OffsetDateTime.parse("2026-09-04T00:00:00+02:00"),
                OffsetDateTime.parse("2026-09-04T03:00:00+02:00"));

        assertThat(selected).isSameAs(second);
    }

    @Test
    void matchesOnTheInstantRegardlessOfOffset() {
        ChargingCandidate osloOffset = candidate(1, "2026-09-04T01:00:00+02:00", "2026-09-04T04:00:00+02:00");

        ChargingCandidate selected = selector.select(List.of(osloOffset),
                OffsetDateTime.parse("2026-09-03T23:00:00Z"),
                OffsetDateTime.parse("2026-09-04T02:00:00Z"));

        assertThat(selected).isSameAs(osloOffset);
    }

    @Test
    void rejectsWithConflictWhenNoCandidateHasThatWindow() {
        ChargingCandidate only = candidate(1, "2026-09-04T01:00:00+02:00", "2026-09-04T04:00:00+02:00");

        assertThatThrownBy(() -> selector.select(List.of(only),
                OffsetDateTime.parse("2026-09-04T00:00:00+02:00"),
                OffsetDateTime.parse("2026-09-04T03:00:00+02:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CHARGING_CANDIDATE_UNAVAILABLE);
    }

    private static ChargingCandidate candidate(int rank, String startAt, String endAt) {
        return new ChargingCandidate(rank,
                OffsetDateTime.parse(startAt),
                OffsetDateTime.parse(endAt),
                new BigDecimal("20.00"),
                new BigDecimal("10.0000"),
                new BigDecimal("14.0000"),
                new BigDecimal("4.0000"),
                List.of());
    }
}
