package com.wattpilot.electricity.monitoring;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.scheduler.PriceCollectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceFreshnessEndpointTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Mock
    private ElectricityPriceService electricityPriceService;

    @Test
    void isUpBeforeTheDeadlineWithoutLookingAtThePrices() {
        // 13:59 Oslo (+01:00 in January): prices are normally still being collected.
        WebEndpointResponse<Map<String, String>> response = endpointAt("2026-01-15T12:59:00Z").check();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
        verify(electricityPriceService, never()).hasCompleteDailyPrices(any(), any());
    }

    @Test
    void isUpAfterTheDeadlineWhenEveryAreaHasTomorrowsPrices() {
        when(electricityPriceService.hasCompleteDailyPrices(any(), any())).thenReturn(true);

        WebEndpointResponse<Map<String, String>> response = endpointAt("2026-01-15T15:00:00Z").check();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
        verify(electricityPriceService).hasCompleteDailyPrices(PriceArea.values()[0], LocalDate.of(2026, 1, 16));
    }

    @Test
    void isDownAfterTheDeadlineWhenASingleAreaIsMissing() {
        when(electricityPriceService.hasCompleteDailyPrices(any(), any())).thenReturn(true);
        PriceArea missing = PriceArea.values()[PriceArea.values().length - 1];
        when(electricityPriceService.hasCompleteDailyPrices(missing, LocalDate.of(2026, 1, 16))).thenReturn(false);

        WebEndpointResponse<Map<String, String>> response = endpointAt("2026-01-15T15:00:00Z").check();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getBody()).containsOnly(Map.entry("status", "DOWN"));
    }

    @Test
    void isDownAtExactlyTheDeadline() {
        when(electricityPriceService.hasCompleteDailyPrices(any(), any())).thenReturn(false);

        // 16:00 Oslo is 15:00Z in January.
        assertThat(endpointAt("2026-01-15T15:00:00Z").check().getStatus()).isEqualTo(503);
    }

    @Test
    void rollsOverToANewTargetDateAtOsloMidnightAndIsUpAgainUntilTheDeadline() {
        // 2026-01-15T23:30Z is 00:30 on 2026-01-16 in Oslo: before the deadline again, nothing is checked.
        WebEndpointResponse<Map<String, String>> response = endpointAt("2026-01-15T23:30:00Z").check();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(electricityPriceService, never()).hasCompleteDailyPrices(any(), any());
    }

    @Test
    void usesTheOsloDateEvenWhenUtcIsStillOnThePreviousDay() {
        when(electricityPriceService.hasCompleteDailyPrices(any(), any())).thenReturn(true);
        PriceFreshnessEndpoint endpoint = endpointAt("2026-06-30T23:30:00Z", LocalTime.of(0, 0));

        endpoint.check();

        // 23:30Z is 01:30 on 2026-07-01 in Oslo (+02:00), so tomorrow is 2026-07-02.
        verify(electricityPriceService).hasCompleteDailyPrices(PriceArea.values()[0], LocalDate.of(2026, 7, 2));
    }

    private PriceFreshnessEndpoint endpointAt(String instant) {
        return endpointAt(instant, LocalTime.of(16, 0));
    }

    private PriceFreshnessEndpoint endpointAt(String instant, LocalTime deadline) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new PriceFreshnessEndpoint(electricityPriceService, clock,
                new PriceCollectionProperties(true, "0 15 13-22 * * *", OSLO, deadline));
    }
}
