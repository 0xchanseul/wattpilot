package com.wattpilot.electricity.service;

import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.ElectricityPriceListResponse;
import com.wattpilot.electricity.dto.ElectricityPriceResponse;
import com.wattpilot.electricity.dto.PriceImportResult;
import com.wattpilot.electricity.dto.PricePoint;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.repository.ElectricityPriceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores electricity prices imported from a provider and serves them back by area and time.
 *
 * <p>V1 has a single provider (Hva koster strømmen) and no per-user pricing, so prices are global:
 * there is no ownership check. The Hva koster strømmen client added in a later step is expected to
 * reuse {@link #importPrices(PriceProvider, PriceArea, List)} to persist what it fetches.
 */
@Service
@Transactional(readOnly = true)
public class ElectricityPriceService {

    /**
     * Norwegian bidding zones follow Oslo wall-clock time (CET/CEST). Day boundaries for a
     * date-based lookup are resolved in this zone so a DST day is still treated as one calendar day.
     */
    public static final ZoneId PRICE_ZONE = ZoneId.of("Europe/Oslo");

    /** V1 reads only ever expose the single provider the app imports from. */
    private static final PriceProvider V1_PROVIDER = PriceProvider.HVA_KOSTER_STROMMEN;

    private static final String DEFAULT_CURRENCY = "NOK";

    private final ElectricityPriceRepository repository;

    public ElectricityPriceService(ElectricityPriceRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a batch of hourly prices for one area, upserting on the
     * {@code (provider, priceArea, startsAt)} key: an hour already stored is overwritten with the
     * fresher price, currency and fetch time; a new hour is inserted.
     *
     * <p>Duplicate start times within {@code slots} are collapsed (last one wins) so a single call
     * cannot race its own unique key.
     */
    @Transactional
    public PriceImportResult importPrices(PriceProvider provider, PriceArea priceArea, List<PriceSlot> slots) {
        if (slots.isEmpty()) {
            return PriceImportResult.empty();
        }

        OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);

        Map<Instant, PriceSlot> incomingByStart = new LinkedHashMap<>();
        for (PriceSlot slot : slots) {
            incomingByStart.put(slot.startsAt().toInstant(), slot);
        }

        Map<Instant, ElectricityPrice> existingByStart = repository
                .findByProviderAndPriceAreaAndStartsAtIn(provider, priceArea, startInstantsAsOffset(incomingByStart.keySet()))
                .stream()
                .collect(Collectors.toMap(price -> price.getStartsAt().toInstant(), Function.identity()));

        List<ElectricityPrice> toSave = new ArrayList<>(incomingByStart.size());
        int inserted = 0;
        int updated = 0;
        for (PriceSlot slot : incomingByStart.values()) {
            ElectricityPrice existing = existingByStart.get(slot.startsAt().toInstant());
            if (existing == null) {
                toSave.add(ElectricityPrice.of(provider, priceArea, slot.startsAt(), slot.endsAt(),
                        slot.pricePerKwh(), currencyOrDefault(slot.currency()), fetchedAt));
                inserted++;
            } else {
                existing.refresh(slot.endsAt(), slot.pricePerKwh(), currencyOrDefault(slot.currency()), fetchedAt);
                toSave.add(existing);
                updated++;
            }
        }
        repository.saveAll(toSave);
        return new PriceImportResult(inserted, updated);
    }

    /**
     * Hours whose start falls in {@code [from, to)} for the area, ordered by start time. Backs
     * {@code GET /electricity-prices}.
     */
    public ElectricityPriceListResponse getPrices(PriceArea priceArea, OffsetDateTime from, OffsetDateTime to) {
        if (!to.isAfter(from)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        List<ElectricityPriceResponse> prices = repository.findRange(V1_PROVIDER, priceArea, from, to).stream()
                .map(price -> ElectricityPriceResponse.from(price, PRICE_ZONE))
                .toList();
        return ElectricityPriceListResponse.of(priceArea, V1_PROVIDER, DEFAULT_CURRENCY, from, to, prices);
    }

    /**
     * All stored hours for one Norwegian calendar day in the given area, ordered by start time.
     *
     * <p>Reused by the later price-fetch step to see which days are already covered. The day spans
     * {@code [00:00, next 00:00)} in Europe/Oslo, so a DST transition day correctly resolves to 23 or
     * 25 hours.
     */
    public List<ElectricityPriceResponse> getDailyPrices(PriceArea priceArea, LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(PRICE_ZONE).toOffsetDateTime();
        OffsetDateTime nextDayStart = date.plusDays(1).atStartOfDay(PRICE_ZONE).toOffsetDateTime();
        return repository.findRange(V1_PROVIDER, priceArea, dayStart, nextDayStart).stream()
                .map(price -> ElectricityPriceResponse.from(price, PRICE_ZONE))
                .toList();
    }

    /**
     * Whether the area already has a full day of prices stored for {@code date}. Lets the collection
     * scheduler skip the external call for areas that are already done, including on its retry runs.
     *
     * <p>"Full" is the number of hours the Oslo day actually has (23, 24 or 25 across a DST switch),
     * derived from the same {@code [00:00, next 00:00)} boundary as {@link #getDailyPrices}.
     */
    public boolean hasCompleteDailyPrices(PriceArea priceArea, LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(PRICE_ZONE).toOffsetDateTime();
        OffsetDateTime nextDayStart = date.plusDays(1).atStartOfDay(PRICE_ZONE).toOffsetDateTime();
        long expectedHours = Duration.between(dayStart, nextDayStart).toHours();
        return repository.countRange(V1_PROVIDER, priceArea, dayStart, nextDayStart) >= expectedHours;
    }

    /**
     * Hourly prices that overlap {@code [from, to)} for the area, ordered by start time. A leading or
     * trailing hour that only partially overlaps the window is included. Backs charging optimization,
     * which prorates those partial hours itself.
     */
    public List<PricePoint> getPricePointsInWindow(PriceArea priceArea, OffsetDateTime from, OffsetDateTime to) {
        return repository.findOverlapping(V1_PROVIDER, priceArea, from, to).stream()
                .map(price -> PricePoint.from(price, PRICE_ZONE))
                .toList();
    }

    /**
     * The price interval covering the current instant for the area. Backs
     * {@code GET /electricity-prices/latest}; a missing interval is a 404.
     */
    public ElectricityPriceResponse getCurrentPrice(PriceArea priceArea) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.findCoveringInstant(V1_PROVIDER, priceArea, now, Limit.of(1)).stream()
                .findFirst()
                .map(price -> ElectricityPriceResponse.from(price, PRICE_ZONE))
                .orElseThrow(() -> new BusinessException(ErrorCode.ELECTRICITY_PRICE_NOT_FOUND));
    }

    private static List<OffsetDateTime> startInstantsAsOffset(Iterable<Instant> instants) {
        List<OffsetDateTime> result = new ArrayList<>();
        for (Instant instant : instants) {
            result.add(instant.atOffset(ZoneOffset.UTC));
        }
        return result;
    }

    private static String currencyOrDefault(String currency) {
        return currency != null ? currency : DEFAULT_CURRENCY;
    }
}
