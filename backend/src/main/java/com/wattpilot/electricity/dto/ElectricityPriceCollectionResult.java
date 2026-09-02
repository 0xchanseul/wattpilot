package com.wattpilot.electricity.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Summary of one scheduler run across all price areas. Returned for logging and assertions; it is
 * not exposed through any API.
 */
public record ElectricityPriceCollectionResult(LocalDate targetDate, List<AreaCollectionOutcome> outcomes) {

    public long countOf(AreaCollectionOutcome.Status status) {
        return outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    /** True when every area is either freshly collected or already stored in full. */
    public boolean isComplete() {
        return outcomes.stream().allMatch(outcome ->
                outcome.status() == AreaCollectionOutcome.Status.COLLECTED
                        || outcome.status() == AreaCollectionOutcome.Status.ALREADY_COMPLETE);
    }
}
