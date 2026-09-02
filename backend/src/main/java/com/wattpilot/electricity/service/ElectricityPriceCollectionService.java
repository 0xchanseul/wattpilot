package com.wattpilot.electricity.service;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.AreaCollectionOutcome;
import com.wattpilot.electricity.dto.ElectricityPriceCollectionResult;
import com.wattpilot.electricity.dto.PriceImportResult;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.provider.ElectricityPriceProvider;
import com.wattpilot.electricity.provider.ElectricityPriceProviderException;
import com.wattpilot.electricity.provider.PricesNotYetPublishedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects a full day of prices for every Norwegian bidding zone from the external provider and
 * stores them through {@link ElectricityPriceService}.
 *
 * <p>Each area is handled independently: a failure or a "not published yet" for one area never stops
 * the others, and an area already stored in full is skipped without an external call. Storage reuses
 * the existing upsert, so running the same date and area again is idempotent.
 */
@Service
public class ElectricityPriceCollectionService {

    private static final Logger log = LoggerFactory.getLogger(ElectricityPriceCollectionService.class);

    private static final List<PriceArea> TARGET_AREAS = List.of(PriceArea.values());
    private static final PriceProvider PROVIDER = PriceProvider.HVA_KOSTER_STROMMEN;

    private final ElectricityPriceProvider priceProvider;
    private final ElectricityPriceService electricityPriceService;

    public ElectricityPriceCollectionService(ElectricityPriceProvider priceProvider,
                                             ElectricityPriceService electricityPriceService) {
        this.priceProvider = priceProvider;
        this.electricityPriceService = electricityPriceService;
    }

    public ElectricityPriceCollectionResult collectForAllAreas(LocalDate targetDate) {
        List<AreaCollectionOutcome> outcomes = new ArrayList<>(TARGET_AREAS.size());
        for (PriceArea area : TARGET_AREAS) {
            outcomes.add(collectArea(targetDate, area));
        }
        ElectricityPriceCollectionResult result = new ElectricityPriceCollectionResult(targetDate, outcomes);
        log.info("Price collection run finished: targetDate={}, collected={}, alreadyComplete={}, notPublished={}, failed={}",
                targetDate,
                result.countOf(AreaCollectionOutcome.Status.COLLECTED),
                result.countOf(AreaCollectionOutcome.Status.ALREADY_COMPLETE),
                result.countOf(AreaCollectionOutcome.Status.NOT_PUBLISHED),
                result.countOf(AreaCollectionOutcome.Status.FAILED));
        return result;
    }

    private AreaCollectionOutcome collectArea(LocalDate targetDate, PriceArea area) {
        try {
            if (electricityPriceService.hasCompleteDailyPrices(area, targetDate)) {
                log.info("Skipped area: targetDate={}, priceArea={}, result=ALREADY_COMPLETE", targetDate, area);
                return AreaCollectionOutcome.alreadyComplete(area);
            }

            List<PriceSlot> slots = priceProvider.fetchDailyPrices(area, targetDate);
            if (slots.isEmpty()) {
                log.warn("No data yet: targetDate={}, priceArea={}, result=NOT_PUBLISHED, reason=empty response",
                        targetDate, area);
                return AreaCollectionOutcome.notPublished(area, "empty response");
            }

            PriceImportResult imported = electricityPriceService.importPrices(PROVIDER, area, slots);
            log.info("Collected area: targetDate={}, priceArea={}, result=COLLECTED, receivedSlotCount={}, "
                            + "insertedCount={}, updatedCount={}",
                    targetDate, area, slots.size(), imported.inserted(), imported.updated());
            return AreaCollectionOutcome.collected(area, slots.size(), imported);

        } catch (PricesNotYetPublishedException ex) {
            log.warn("No data yet: targetDate={}, priceArea={}, result=NOT_PUBLISHED, reason={}",
                    targetDate, area, ex.getMessage());
            return AreaCollectionOutcome.notPublished(area, ex.getMessage());
        } catch (ElectricityPriceProviderException ex) {
            log.error("Provider call failed: targetDate={}, priceArea={}, result=FAILED, reason={}",
                    targetDate, area, ex.getMessage());
            return AreaCollectionOutcome.failed(area, ex.getMessage());
        } catch (RuntimeException ex) {
            // A store failure or any other unexpected error: isolate this area, keep going.
            log.error("Unexpected failure: targetDate={}, priceArea={}, result=FAILED", targetDate, area, ex);
            return AreaCollectionOutcome.failed(area, ex.toString());
        }
    }
}
