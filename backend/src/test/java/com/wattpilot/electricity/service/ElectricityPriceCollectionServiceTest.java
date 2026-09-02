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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectricityPriceCollectionServiceTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 3);

    @Mock
    private ElectricityPriceProvider priceProvider;

    @Mock
    private ElectricityPriceService electricityPriceService;

    @InjectMocks
    private ElectricityPriceCollectionService collectionService;

    @Test
    void collectsEveryAreaAndStoresThroughTheExistingImportMethod() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(electricityPriceService.importPrices(eq(PriceProvider.HVA_KOSTER_STROMMEN), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(result.outcomes()).hasSize(5);
        assertThat(result.countOf(AreaCollectionOutcome.Status.COLLECTED)).isEqualTo(5);
        for (PriceArea area : PriceArea.values()) {
            verify(priceProvider).fetchDailyPrices(area, TARGET_DATE);
            verify(electricityPriceService).importPrices(PriceProvider.HVA_KOSTER_STROMMEN, area, oneSlot());
        }
    }

    @Test
    void oneAreaFailingDoesNotStopTheOthers() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(priceProvider.fetchDailyPrices(eq(PriceArea.NO3), eq(TARGET_DATE)))
                .thenThrow(new ElectricityPriceProviderException("boom"));
        when(electricityPriceService.importPrices(any(), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(outcomeFor(result, PriceArea.NO3).status()).isEqualTo(AreaCollectionOutcome.Status.FAILED);
        assertThat(result.countOf(AreaCollectionOutcome.Status.COLLECTED)).isEqualTo(4);
        verify(priceProvider).fetchDailyPrices(PriceArea.NO4, TARGET_DATE);
        verify(priceProvider).fetchDailyPrices(PriceArea.NO5, TARGET_DATE);
    }

    @Test
    void pricesNotPublishedYetIsReportedAsRetryableNotFailed() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(priceProvider.fetchDailyPrices(eq(PriceArea.NO2), eq(TARGET_DATE)))
                .thenThrow(new PricesNotYetPublishedException("not yet"));
        when(electricityPriceService.importPrices(any(), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(outcomeFor(result, PriceArea.NO2).status()).isEqualTo(AreaCollectionOutcome.Status.NOT_PUBLISHED);
        assertThat(result.countOf(AreaCollectionOutcome.Status.FAILED)).isZero();
    }

    @Test
    void anEmptyProviderResponseIsTreatedAsNotPublished() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(priceProvider.fetchDailyPrices(eq(PriceArea.NO1), eq(TARGET_DATE))).thenReturn(List.of());
        when(electricityPriceService.importPrices(any(), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(outcomeFor(result, PriceArea.NO1).status()).isEqualTo(AreaCollectionOutcome.Status.NOT_PUBLISHED);
        verify(electricityPriceService, never()).importPrices(any(), eq(PriceArea.NO1), anyList());
    }

    @Test
    void areasAlreadyStoredInFullAreSkippedWithoutAnExternalCall() {
        when(electricityPriceService.hasCompleteDailyPrices(PriceArea.NO1, TARGET_DATE)).thenReturn(true);
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(electricityPriceService.importPrices(any(), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(outcomeFor(result, PriceArea.NO1).status()).isEqualTo(AreaCollectionOutcome.Status.ALREADY_COMPLETE);
        verify(priceProvider, never()).fetchDailyPrices(PriceArea.NO1, TARGET_DATE);
        assertThat(result.countOf(AreaCollectionOutcome.Status.COLLECTED)).isEqualTo(4);
    }

    @Test
    void aStoreFailureIsIsolatedToItsArea() {
        when(priceProvider.fetchDailyPrices(any(), eq(TARGET_DATE))).thenReturn(oneSlot());
        when(electricityPriceService.importPrices(any(), any(), anyList()))
                .thenReturn(new PriceImportResult(1, 0));
        when(electricityPriceService.importPrices(eq(PriceProvider.HVA_KOSTER_STROMMEN), eq(PriceArea.NO4), anyList()))
                .thenThrow(new RuntimeException("db down"));

        ElectricityPriceCollectionResult result = collectionService.collectForAllAreas(TARGET_DATE);

        assertThat(outcomeFor(result, PriceArea.NO4).status()).isEqualTo(AreaCollectionOutcome.Status.FAILED);
        assertThat(result.countOf(AreaCollectionOutcome.Status.COLLECTED)).isEqualTo(4);
    }

    private static AreaCollectionOutcome outcomeFor(ElectricityPriceCollectionResult result, PriceArea area) {
        return result.outcomes().stream().filter(o -> o.area() == area).findFirst().orElseThrow();
    }

    private static List<PriceSlot> oneSlot() {
        return List.of(new PriceSlot(
                OffsetDateTime.parse("2026-09-03T00:00:00+02:00"),
                OffsetDateTime.parse("2026-09-03T01:00:00+02:00"),
                new BigDecimal("1.3682"), "NOK"));
    }
}
