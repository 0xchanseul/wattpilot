package com.wattpilot.scheduler;

import com.wattpilot.electricity.service.ElectricityPriceCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fires the daily next-day electricity price collection.
 *
 * <p>V1 collects centrally: user requests and the optimizer always read prices from the database,
 * never from the external API. The cron (see {@link PriceCollectionProperties}) fires the first
 * attempt at 13:15 Oslo and retries hourly until 22:15; {@link ElectricityPriceCollectionService}
 * skips areas that are already stored, so the retries are cheap and stop doing work once every zone
 * is in. No sleeping, no unbounded retry loop — the cron schedule is the retry mechanism.
 */
@Component
@ConditionalOnProperty(prefix = "wattpilot.electricity.collection", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class PriceCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceCollectionScheduler.class);

    private final ElectricityPriceCollectionService collectionService;
    private final Clock clock;
    private final ZoneId zone;

    public PriceCollectionScheduler(ElectricityPriceCollectionService collectionService, Clock clock,
                                    PriceCollectionProperties properties) {
        this.collectionService = collectionService;
        this.clock = clock;
        this.zone = properties.zone();
    }

    @Scheduled(cron = "${wattpilot.electricity.collection.cron}",
            zone = "${wattpilot.electricity.collection.zone:Europe/Oslo}")
    public void collectUpcomingPrices() {
        LocalDate targetDate = LocalDate.now(clock.withZone(zone)).plusDays(1);
        log.info("Starting scheduled price collection for targetDate={} (zone={})", targetDate, zone);
        collectionService.collectForAllAreas(targetDate);
    }
}
