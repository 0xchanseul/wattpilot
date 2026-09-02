package com.wattpilot.electricity.service;

import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.ElectricityPriceListResponse;
import com.wattpilot.electricity.dto.ElectricityPriceResponse;
import com.wattpilot.electricity.dto.PriceImportResult;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.ElectricityPrice;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.repository.ElectricityPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectricityPriceServiceTest {

    private static final PriceProvider PROVIDER = PriceProvider.HVA_KOSTER_STROMMEN;
    private static final ZoneOffset OSLO_SUMMER = ZoneOffset.ofHours(2);

    @Mock
    private ElectricityPriceRepository repository;

    @InjectMocks
    private ElectricityPriceService service;

    @Captor
    private ArgumentCaptor<List<ElectricityPrice>> savedCaptor;

    @Test
    void importPricesInsertsUnknownHoursNormalisedToUtc() {
        when(repository.findByProviderAndPriceAreaAndStartsAtIn(eq(PROVIDER), eq(PriceArea.NO1), anyCollection()))
                .thenReturn(List.of());

        PriceImportResult result = service.importPrices(PROVIDER, PriceArea.NO1, List.of(
                slot("2026-08-24T01:00:00+02:00", "2026-08-24T02:00:00+02:00", "0.71"),
                slot("2026-08-24T02:00:00+02:00", "2026-08-24T03:00:00+02:00", "0.64")));

        assertThat(result).isEqualTo(new PriceImportResult(2, 0));
        verify(repository).saveAll(savedCaptor.capture());
        List<ElectricityPrice> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getStartsAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(saved.get(0).getStartsAt().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-08-24T01:00:00+02:00").toInstant());
        assertThat(saved.get(0).getCurrency()).isEqualTo("NOK");
        assertThat(saved.get(0).getProvider()).isEqualTo(PROVIDER);
        assertThat(saved.get(0).getPriceArea()).isEqualTo(PriceArea.NO1);
    }

    @Test
    void importPricesOverwritesAnExistingHourInsteadOfDuplicatingIt() {
        ElectricityPrice existing = ElectricityPrice.of(PROVIDER, PriceArea.NO1,
                OffsetDateTime.parse("2026-08-24T01:00:00+02:00"),
                OffsetDateTime.parse("2026-08-24T02:00:00+02:00"),
                new BigDecimal("0.71"), "NOK", OffsetDateTime.parse("2026-08-23T12:00:00Z"));
        when(repository.findByProviderAndPriceAreaAndStartsAtIn(eq(PROVIDER), eq(PriceArea.NO1), anyCollection()))
                .thenReturn(List.of(existing));

        PriceImportResult result = service.importPrices(PROVIDER, PriceArea.NO1, List.of(
                slot("2026-08-24T01:00:00+02:00", "2026-08-24T02:00:00+02:00", "0.90")));

        assertThat(result).isEqualTo(new PriceImportResult(0, 1));
        assertThat(existing.getPricePerKwh()).isEqualByComparingTo("0.90");
        verify(repository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).containsExactly(existing);
    }

    @Test
    void importPricesCollapsesDuplicateStartTimesWithinOneBatch() {
        when(repository.findByProviderAndPriceAreaAndStartsAtIn(eq(PROVIDER), eq(PriceArea.NO1), anyCollection()))
                .thenReturn(List.of());

        PriceImportResult result = service.importPrices(PROVIDER, PriceArea.NO1, List.of(
                slot("2026-08-24T01:00:00+02:00", "2026-08-24T02:00:00+02:00", "0.71"),
                slot("2026-08-24T01:00:00+02:00", "2026-08-24T02:00:00+02:00", "0.99")));

        assertThat(result).isEqualTo(new PriceImportResult(1, 0));
        verify(repository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().get(0).getPricePerKwh()).isEqualByComparingTo("0.99");
    }

    @Test
    void importPricesWithNoSlotsTouchesNothing() {
        assertThat(service.importPrices(PROVIDER, PriceArea.NO1, List.of())).isEqualTo(PriceImportResult.empty());
        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void getPricesRejectsAnInvertedWindow() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-24T10:00:00Z");
        assertThatThrownBy(() -> service.getPrices(PriceArea.NO1, from, from))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void getPricesReturnsHoursRenderedInOsloTimeWrappedInTheListEnvelope() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-24T00:00:00+02:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-08-25T00:00:00+02:00");
        when(repository.findRange(PROVIDER, PriceArea.NO1, from, to)).thenReturn(List.of(
                ElectricityPrice.of(PROVIDER, PriceArea.NO1,
                        OffsetDateTime.parse("2026-08-24T01:00:00+02:00"),
                        OffsetDateTime.parse("2026-08-24T02:00:00+02:00"),
                        new BigDecimal("0.71"), "NOK", OffsetDateTime.parse("2026-08-23T12:00:00Z"))));

        ElectricityPriceListResponse response = service.getPrices(PriceArea.NO1, from, to);

        assertThat(response.priceArea()).isEqualTo(PriceArea.NO1);
        assertThat(response.provider()).isEqualTo(PROVIDER);
        assertThat(response.currency()).isEqualTo("NOK");
        assertThat(response.prices()).hasSize(1);
        ElectricityPriceResponse price = response.prices().get(0);
        assertThat(price.startsAt()).isEqualTo(OffsetDateTime.parse("2026-08-24T01:00:00+02:00"));
        assertThat(price.startsAt().getOffset()).isEqualTo(OSLO_SUMMER);
    }

    @Test
    void getDailyPricesResolvesTheOsloDayEvenAcrossASpringForwardTransition() {
        // 2026-03-29 is the Norwegian spring-forward day: it starts at +01:00 and ends at +02:00.
        when(repository.findRange(eq(PROVIDER), eq(PriceArea.NO2), any(), any())).thenReturn(List.of());

        service.getDailyPrices(PriceArea.NO2, LocalDate.of(2026, 3, 29));

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).findRange(eq(PROVIDER), eq(PriceArea.NO2), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-03-29T00:00:00+01:00").toInstant());
        assertThat(toCaptor.getValue().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-03-30T00:00:00+02:00").toInstant());
    }

    @Test
    void hasCompleteDailyPricesComparesTheStoredCountAgainstTheOsloDayLength() {
        when(repository.countRange(eq(PROVIDER), eq(PriceArea.NO1), any(), any())).thenReturn(24L);
        assertThat(service.hasCompleteDailyPrices(PriceArea.NO1, LocalDate.of(2026, 8, 24))).isTrue();

        when(repository.countRange(eq(PROVIDER), eq(PriceArea.NO2), any(), any())).thenReturn(23L);
        assertThat(service.hasCompleteDailyPrices(PriceArea.NO2, LocalDate.of(2026, 8, 24))).isFalse();
    }

    @Test
    void hasCompleteDailyPricesExpects25HoursOnAFallBackDstDay() {
        // 2026-10-25 is the Norwegian fall-back day: 00:00+02:00 .. next 00:00+01:00 spans 25 hours.
        when(repository.countRange(eq(PROVIDER), eq(PriceArea.NO1), any(), any())).thenReturn(24L);

        assertThat(service.hasCompleteDailyPrices(PriceArea.NO1, LocalDate.of(2026, 10, 25))).isFalse();

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).countRange(eq(PROVIDER), eq(PriceArea.NO1), from.capture(), to.capture());
        assertThat(from.getValue().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-25T00:00:00+02:00").toInstant());
        assertThat(to.getValue().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-26T00:00:00+01:00").toInstant());
    }

    @Test
    void getCurrentPriceReturns404WhenNoIntervalCoversNow() {
        when(repository.findCoveringInstant(eq(PROVIDER), eq(PriceArea.NO1), any(), eq(Limit.of(1))))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getCurrentPrice(PriceArea.NO1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ELECTRICITY_PRICE_NOT_FOUND);
    }

    private static PriceSlot slot(String startsAt, String endsAt, String pricePerKwh) {
        return new PriceSlot(OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt),
                new BigDecimal(pricePerKwh), null);
    }
}
