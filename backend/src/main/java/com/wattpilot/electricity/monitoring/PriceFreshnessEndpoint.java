package com.wattpilot.electricity.monitoring;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.scheduler.PriceCollectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tells an external uptime monitor whether tomorrow's electricity prices are in the database.
 *
 * <p>This is a separate endpoint and not a health indicator on purpose: every indicator is part of
 * the aggregate {@code /actuator/health}, which the API uptime monitor and the deploy rollback check
 * both read. A missing-price condition must raise an alert, not mark the whole application down or
 * roll back a deploy.
 *
 * <p>It judges the stored result and not the collection job, so it also catches a job that never ran,
 * a provider outage and a restart in the middle of the retry window. Before the deadline missing
 * prices are normal (they are published shortly after 13:00 Oslo), so it answers {@code UP}. The
 * answer carries no area names; they go to the log only, like the rest of the health surface.
 */
@Component
@Endpoint(id = "pricefreshness")
public class PriceFreshnessEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PriceFreshnessEndpoint.class);

    private static final int HTTP_OK = 200;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private final ElectricityPriceService electricityPriceService;
    private final Clock clock;
    private final ZoneId zone;
    private final LocalTime deadline;

    public PriceFreshnessEndpoint(ElectricityPriceService electricityPriceService, Clock clock,
                                  PriceCollectionProperties properties) {
        this.electricityPriceService = electricityPriceService;
        this.clock = clock;
        this.zone = properties.zone();
        this.deadline = properties.freshnessDeadline();
    }

    @ReadOperation
    public WebEndpointResponse<Map<String, String>> check() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        if (now.toLocalTime().isBefore(deadline)) {
            return response(true);
        }

        LocalDate targetDate = now.toLocalDate().plusDays(1);
        List<PriceArea> missing = List.of(PriceArea.values()).stream()
                .filter(area -> !electricityPriceService.hasCompleteDailyPrices(area, targetDate))
                .toList();
        if (missing.isEmpty()) {
            return response(true);
        }

        log.error("Price freshness check failed: targetDate={}, deadline={}, missingAreas={}",
                targetDate, deadline, missing);
        return response(false);
    }

    private static WebEndpointResponse<Map<String, String>> response(boolean fresh) {
        return new WebEndpointResponse<>(Map.of("status", fresh ? "UP" : "DOWN"),
                fresh ? HTTP_OK : HTTP_SERVICE_UNAVAILABLE);
    }
}
