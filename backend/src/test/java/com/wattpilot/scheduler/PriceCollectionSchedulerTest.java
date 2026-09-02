package com.wattpilot.scheduler;

import com.wattpilot.electricity.service.ElectricityPriceCollectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceCollectionSchedulerTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Mock
    private ElectricityPriceCollectionService collectionService;

    @Test
    void targetsTomorrowInOsloEvenWhenUtcIsStillOnThePreviousDay() {
        // 2026-06-30T23:30Z is already 2026-07-01 01:30 in Oslo (+02:00), so "tomorrow" is 2026-07-02.
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T23:30:00Z"), ZoneOffset.UTC);
        scheduler(clock).collectUpcomingPrices();

        verify(collectionService).collectForAllAreas(LocalDate.of(2026, 7, 2));
    }

    @Test
    void targetsTomorrowInOsloForAnAfternoonRun() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:15:00Z"), ZoneOffset.UTC);
        scheduler(clock).collectUpcomingPrices();

        verify(collectionService).collectForAllAreas(LocalDate.of(2026, 1, 16));
    }

    private PriceCollectionScheduler scheduler(Clock clock) {
        return new PriceCollectionScheduler(collectionService, clock,
                new PriceCollectionProperties(true, "0 15 13-22 * * *", OSLO));
    }
}
