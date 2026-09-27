package com.wattpilot.savings.service;

import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.savings.dto.DailySavings;
import com.wattpilot.savings.dto.Granularity;
import com.wattpilot.savings.dto.PatternGroupBy;
import com.wattpilot.savings.dto.SavingsPatternPoint;
import com.wattpilot.savings.dto.SavingsSummary;
import com.wattpilot.savings.repository.SavingsAggregateRow;
import com.wattpilot.savings.repository.SavingsPatternSlotRow;
import com.wattpilot.savings.repository.SavingsRepository;
import com.wattpilot.savings.repository.SavingsSessionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Builds the {@code GET /savings/summary}, {@code GET /savings/daily} and
 * {@code GET /savings/patterns} payloads — a read model over {@code charging_sessions ->
 * charging_schedules -> charging_plans}, the same join {@code
 * com.wattpilot.dashboard.service.DashboardService} and {@code
 * com.wattpilot.history.service.ChargingHistoryService} use. Unlike the Dashboard's fixed 30-day
 * window, {@code from}/{@code to} here are caller-supplied Europe/Oslo calendar dates, both
 * inclusive.
 *
 * <p>{@code granularity=MONTHLY} buckets by Europe/Oslo calendar month instead of day, with
 * {@code date} set to the first of the month. Bucketing happens here in Java for the same reason
 * the Dashboard buckets by day in Java: a timezone-aware {@code GROUP BY} has no portable JPQL
 * expression, and a single user's session volume over a reporting range is small.
 *
 * <p>{@code getPatterns} buckets by a recurring weekday or hour of day instead of by calendar date,
 * and does so per charging plan slot rather than per whole session: a session that runs across
 * several price hours (or across midnight) must contribute to every hour/weekday it actually ran
 * in, not only the one it started in. Each slot's own {@code expectedCostNok} is its optimized cost;
 * the session's single {@code baselineCostNok} has no per-slot equivalent, so it is prorated across
 * the session's slots by each slot's share of the session's {@code actualEnergyKwh}.
 */
@Service
@Transactional(readOnly = true)
public class SavingsService {

    private static final ZoneId ZONE = ElectricityPriceService.PRICE_ZONE;
    private static final int PRORATION_SCALE = 10;

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

    public List<SavingsPatternPoint> getPatterns(Long userId, LocalDate from, LocalDate to, Long evId, PatternGroupBy groupBy) {
        validateRange(from, to);
        OffsetDateTime since = startOfDay(from);
        OffsetDateTime until = startOfDay(to.plusDays(1));

        List<SavingsPatternSlotRow> rows = evId != null
                ? savingsRepository.findSlotsInRangeByEv(userId, evId, ChargingSessionStatus.COMPLETED, since, until)
                : savingsRepository.findSlotsInRange(userId, ChargingSessionStatus.COMPLETED, since, until);

        Map<Integer, PatternBucket> byBucket = new LinkedHashMap<>();
        for (SavingsPatternSlotRow row : rows) {
            int bucketKey = patternBucketKey(row.slotStartAt(), groupBy);
            byBucket.computeIfAbsent(bucketKey, key -> new PatternBucket()).add(row);
        }

        return patternBucketKeys(groupBy).stream()
                .map(bucket -> {
                    PatternBucket patternBucket = byBucket.get(bucket);
                    return patternBucket == null ? SavingsPatternPoint.zero(bucket) : patternBucket.toPoint(bucket);
                })
                .toList();
    }

    /** ISO-8601 day of week (1=Monday..7=Sunday) or hour of day (0-23), resolved in Europe/Oslo. */
    private int patternBucketKey(OffsetDateTime slotStartAt, PatternGroupBy groupBy) {
        var zoned = slotStartAt.atZoneSameInstant(ZONE);
        return groupBy == PatternGroupBy.HOUR_OF_DAY ? zoned.getHour() : zoned.getDayOfWeek().getValue();
    }

    /** Every bucket key for the group-by, ascending: 1-7 for weekdays, 0-23 for hours. */
    private List<Integer> patternBucketKeys(PatternGroupBy groupBy) {
        return groupBy == PatternGroupBy.HOUR_OF_DAY
                ? IntStream.range(0, 24).boxed().toList()
                : IntStream.rangeClosed(1, 7).boxed().toList();
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
            return DailySavings.of(date, sessionCount, energyKwh, actualCostNok, baselineCostNok);
        }
    }

    /**
     * Accumulates one weekday/hour-of-day bucket's charging plan slots. {@code sessionIds} is a set,
     * not a running count, because a single session's slots can land in the same bucket more than
     * once (e.g. two of its hourly slots both fall on a Monday for {@code groupBy=WEEKDAY}), and such
     * a session must still count once toward that bucket's {@code sessionCount}.
     */
    private static final class PatternBucket {
        private final Set<Long> sessionIds = new HashSet<>();
        private BigDecimal energyKwh = BigDecimal.ZERO;
        private BigDecimal optimizedCostNok = BigDecimal.ZERO;
        private BigDecimal baselineCostNok = BigDecimal.ZERO;

        void add(SavingsPatternSlotRow row) {
            sessionIds.add(row.sessionId());
            energyKwh = energyKwh.add(row.plannedEnergyKwh());
            optimizedCostNok = optimizedCostNok.add(row.expectedCostNok());
            baselineCostNok = baselineCostNok.add(prorateBaseline(row));
        }

        /** The session's whole-window baseline, scaled down by this slot's share of the session's actual energy. */
        private BigDecimal prorateBaseline(SavingsPatternSlotRow row) {
            if (row.sessionActualEnergyKwh().compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal energyShare = row.plannedEnergyKwh()
                    .divide(row.sessionActualEnergyKwh(), PRORATION_SCALE, RoundingMode.HALF_UP);
            return row.sessionBaselineCostNok().multiply(energyShare);
        }

        SavingsPatternPoint toPoint(int bucket) {
            return SavingsPatternPoint.of(bucket, sessionIds.size(), energyKwh, optimizedCostNok, baselineCostNok);
        }
    }
}
