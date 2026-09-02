package com.wattpilot.electricity;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.AreaCollectionOutcome;
import com.wattpilot.electricity.dto.ElectricityPriceCollectionResult;
import com.wattpilot.electricity.dto.ElectricityPriceResponse;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.provider.ElectricityPriceProvider;
import com.wattpilot.electricity.provider.ElectricityPriceProviderException;
import com.wattpilot.electricity.service.ElectricityPriceCollectionService;
import com.wattpilot.electricity.service.ElectricityPriceService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link ElectricityPriceCollectionService} against a real PostgreSQL instance with the
 * external provider mocked. Verifies the fetch → convert → upsert path, idempotency on re-collection,
 * the skip of already-complete areas, and per-area failure isolation.
 */
@SpringBootTest
@Testcontainers
class ElectricityPriceCollectionIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 10);

    @MockitoBean
    private ElectricityPriceProvider priceProvider;

    @Autowired
    private ElectricityPriceCollectionService collectionService;

    @Autowired
    private ElectricityPriceService electricityPriceService;

    @Test
    void collectsAllFiveAreasAndStoresEveryHour() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(hourlySlots("0.50"));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(result.countOf(AreaCollectionOutcome.Status.COLLECTED)).isEqualTo(5);
        for (PriceArea area : PriceArea.values()) {
            assertThat(electricityPriceService.getDailyPrices(area, TARGET_DATE)).hasSize(24);
        }
    }

    @Test
    void reCollectingAfterAPartialStoreFillsTheGapAndUpdatesPricesWithoutDuplicates() {
        // Pre-store only the first 10 hours at an old price, directly through the existing import.
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO1,
                hourlySlots("0.10").subList(0, 10));
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(hourlySlots("0.90"));

        collectionService.collectForAllAreas(TARGET_DATE);

        List<ElectricityPriceResponse> stored = electricityPriceService.getDailyPrices(PriceArea.NO1, TARGET_DATE);
        assertThat(stored).hasSize(24);
        assertThat(stored).allSatisfy(price -> assertThat(price.pricePerKwh()).isEqualByComparingTo("0.90"));
    }

    @Test
    void areasAlreadyStoredInFullAreNotFetchedAgain() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(hourlySlots("0.50"));
        collectionService.collectForAllAreas(TARGET_DATE);
        Mockito.clearInvocations(priceProvider);

        ElectricityPriceCollectionResult second = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(second.countOf(AreaCollectionOutcome.Status.ALREADY_COMPLETE)).isEqualTo(5);
        verify(priceProvider, never()).fetchDailyPrices(any(), any());
    }

    @Test
    void aProviderFailureForOneAreaLeavesTheOtherAreasCollected() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(hourlySlots("0.50"));
        when(priceProvider.fetchDailyPrices(eq(PriceArea.NO3), eq(TARGET_DATE)))
                .thenThrow(new ElectricityPriceProviderException("simulated outage"));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(result.outcomes().stream()
                .filter(o -> o.area() == PriceArea.NO3).findFirst().orElseThrow().status())
                .isEqualTo(AreaCollectionOutcome.Status.FAILED);
        assertThat(electricityPriceService.getDailyPrices(PriceArea.NO3, TARGET_DATE)).isEmpty();
        assertThat(electricityPriceService.getDailyPrices(PriceArea.NO4, TARGET_DATE)).hasSize(24);
        assertThat(electricityPriceService.getDailyPrices(PriceArea.NO5, TARGET_DATE)).hasSize(24);
    }

    private static List<PriceSlot> hourlySlots(String pricePerKwh) {
        List<PriceSlot> slots = new ArrayList<>();
        OffsetDateTime cursor = OffsetDateTime.parse("2026-09-10T00:00:00+02:00");
        for (int hour = 0; hour < 24; hour++) {
            slots.add(new PriceSlot(cursor, cursor.plusHours(1), new BigDecimal(pricePerKwh), "NOK"));
            cursor = cursor.plusHours(1);
        }
        return slots;
    }
}
