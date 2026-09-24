package com.wattpilot.savings.service;

import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.savings.dto.DailySavings;
import com.wattpilot.savings.dto.Granularity;
import com.wattpilot.savings.dto.SavingsSummary;
import com.wattpilot.savings.repository.SavingsAggregateRow;
import com.wattpilot.savings.repository.SavingsRepository;
import com.wattpilot.savings.repository.SavingsSessionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code GET /savings/summary} and {@code GET /savings/daily} payloads — a read model
 * over {@code charging_sessions -> charging_schedules -> charging_plans}, the same join
 * {@code com.wattpilot.dashboard.service.DashboardService} and
 * {@code com.wattpilot.history.service.ChargingHistoryService} use. Unlike the Dashboard's fixed
 * 30-day window, {@code from}/{@code to} here are caller-supplied Europe/Oslo calendar dates, both
 * inclusive.
 *
 * <p>{@code granularity=MONTHLY} buckets by Europe/Oslo calendar month instead of day, with
 * {@code date} set to the first of the month. Bucketing happens here in Java for the same reason
 * the Dashboard buckets by day in Java: a timezone-aware {@code GROUP BY} has no portable JPQL
 * expression, and a single user's session volume over a reporting range is small.
 */
@Service
@Transactional(readOnly = true)
public class SavingsService {

    private static final ZoneId ZONE = ElectricityPriceService.PRICE_ZONE;

    private final SavingsRepository savingsRepository;

    public SavingsService(SavingsRepository savingsRepository) {
        this.savingsRepository = savingsRepository;
    }

    public SavingsSummary getSummary(Long userId, LocalDate from, LocalDate to, Long evId) {
        validateRange(from, to);
        OffsetDateTime since = startOfDay(from);
        OffsetDateTime until = startOfDay(to.plusDays(1));

        SavingsAggregateRow row = evId != null
                ? savingsRepository.summarizeByEv(userId, evId, ChargingSessionStatus.COMPLETED, since, until)
                : savingsRepository.summarize(userId, ChargingSessionStatus.COMPLETED, since, until);

        return SavingsSummary.of(from, to, evId, row);
    }

    public List<DailySavings> getDaily(Long userId, LocalDate from, LocalDate to, Long evId, Granularity granularity) {
        validateRange(from, to);
        OffsetDateTime since = startOfDay(from);
        OffsetDateTime until = startOfDay(to.plusDays(1));
        Granularity resolved = granularity == null ? Granularity.DAILY : granularity;

        List<SavingsSessionRow> rows = evId != null
                ? savingsRepository.findCompletedInRangeByEv(userId, evId, ChargingSessionStatus.COMPLETED, since, until)
                : savingsRepository.findCompletedInRange(userId, ChargingSessionStatus.COMPLETED, since, until);

        Map<LocalDate, Bucket> byBucket = new LinkedHashMap<>();
        for (SavingsSessionRow row : rows) {
            LocalDate bucketKey = bucketKey(row.completedAt(), resolved);
            byBucket.computeIfAbsent(bucketKey, key -> new Bucket()).add(row);
        }

        return bucketKeys(from, to, resolved).stream()
                .map(date -> {
                    Bucket bucket = byBucket.get(date);
                    return bucket == null ? DailySavings.zero(date) : bucket.toDailySavings(date);
                })
                .toList();
    }

    /** Unlike the strictly-exclusive window `GET /electricity-prices` uses, a single-day range (from == to) is valid here. */
    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "'to' must not be before 'from'.");
        }
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    private LocalDate bucketKey(OffsetDateTime completedAt, Granularity granularity) {
        LocalDate date = completedAt.atZoneSameInstant(ZONE).toLocalDate();
        return granularity == Granularity.MONTHLY ? date.withDayOfMonth(1) : date;
    }

    /** Every bucket start date in `[from, to]`, ascending — daily dates or first-of-month dates. */
    private List<LocalDate> bucketKeys(LocalDate from, LocalDate to, Granularity granularity) {
        if (granularity == Granularity.MONTHLY) {
            LocalDate start = from.withDayOfMonth(1);
            LocalDate end = to.withDayOfMonth(1);
            List<LocalDate> keys = new ArrayList<>();
            for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusMonths(1)) {
                keys.add(cursor);
            }
            return keys;
        }
        return from.datesUntil(to.plusDays(1)).toList();
    }

    private static final class Bucket {
        private int sessionCount;
        private BigDecimal energyKwh = BigDecimal.ZERO;
        private BigDecimal baselineCostNok = BigDecimal.ZERO;
        private BigDecimal actualCostNok = BigDecimal.ZERO;

        void add(SavingsSessionRow row) {
            sessionCount++;
            energyKwh = energyKwh.add(row.actualEnergyKwh());
            baselineCostNok = baselineCostNok.add(row.baselineCostNok());
            actualCostNok = actualCostNok.add(row.actualCostNok());
        }

        DailySavings toDailySavings(LocalDate date) {
            return new DailySavings(date, "NOK", sessionCount, energyKwh, actualCostNok, baselineCostNok,
                    baselineCostNok.subtract(actualCostNok));
        }
    }
}
