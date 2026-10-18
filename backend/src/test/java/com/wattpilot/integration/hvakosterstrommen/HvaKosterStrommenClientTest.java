package com.wattpilot.integration.hvakosterstrommen;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.provider.ElectricityPriceProviderException;
import com.wattpilot.electricity.provider.PricesNotYetPublishedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class HvaKosterStrommenClientTest {

    private static final String BASE_URL = "https://www.hvakosterstrommen.no/api/v1";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private HvaKosterStrommenClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HvaKosterStrommenClient(builder.build());
    }

    @Test
    void buildsPathWithTwoDigitMonthAndDayPerArea() {
        assertThat(HvaKosterStrommenClient.buildPath(PriceArea.NO1, LocalDate.of(2026, 9, 3)))
                .isEqualTo("/prices/2026/09-03_NO1.json");
        assertThat(HvaKosterStrommenClient.buildPath(PriceArea.NO5, LocalDate.of(2026, 1, 5)))
                .isEqualTo("/prices/2026/01-05_NO5.json");
        assertThat(HvaKosterStrommenClient.buildPath(PriceArea.NO3, LocalDate.of(2026, 12, 25)))
                .isEqualTo("/prices/2026/12-25_NO3.json");
    }

    @Test
    void parsesTheJsonArrayAndMapsNokPriceAndHourBoundaries() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [
                          {"NOK_per_kWh":1.3682,"EUR_per_kWh":0.13242,"EXR":10.3323,
                           "time_start":"2026-09-03T00:00:00+02:00","time_end":"2026-09-03T01:00:00+02:00"},
                          {"NOK_per_kWh":1.1900,"EUR_per_kWh":0.11520,"EXR":10.3323,
                           "time_start":"2026-09-03T01:00:00+02:00","time_end":"2026-09-03T02:00:00+02:00"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<PriceSlot> slots = client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3));

        server.verify();
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-03T00:00:00+02:00"));
        assertThat(slots.get(0).endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-03T01:00:00+02:00"));
        assertThat(slots.get(0).pricePerKwh()).isEqualByComparingTo("1.3682");
        assertThat(slots.get(0).currency()).isEqualTo("NOK");
        assertThat(slots.get(1).pricePerKwh()).isEqualByComparingTo("1.1900");
    }

    @Test
    void doesNotAssumeTwentyFourSlotsOnADstDay() {
        StringBuilder json = new StringBuilder("[");
        OffsetDateTime cursor = OffsetDateTime.parse("2026-10-25T00:00:00+02:00");
        for (int hour = 0; hour < 25; hour++) {
            if (hour > 0) {
                json.append(',');
            }
            OffsetDateTime end = cursor.plusHours(1);
            json.append("""
                    {"NOK_per_kWh":0.5,"EUR_per_kWh":0.05,"EXR":10.0,
                     "time_start":"%s","time_end":"%s"}
                    """.formatted(cursor, end));
            cursor = end;
        }
        json.append(']');
        server.expect(requestTo(BASE_URL + "/prices/2026/10-25_NO2.json"))
                .andRespond(withSuccess(json.toString(), MediaType.APPLICATION_JSON));

        List<PriceSlot> slots = client.fetchDailyPrices(PriceArea.NO2, LocalDate.of(2026, 10, 25));

        assertThat(slots).hasSize(25);
    }

    @Test
    void keepsEveryHourOfAFallBackDayWithTheOffsetTheProviderSent() {
        // 2026-10-25 has 25 real hours: 00:00-02:00 at +02:00, then 02:00-24:00 again at +01:00.
        List<PriceSlot> slots = fetchOsloDay(PriceArea.NO2, LocalDate.of(2026, 10, 25), "2026-10-24T22:00:00Z", 25);

        assertThat(slots).hasSize(25);
        // Jackson normalises the offsets to UTC, so the real instants are what must be preserved.
        assertThat(slots.get(2).startsAt().toInstant()).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
        assertThat(slots.get(3).startsAt().toInstant()).isEqualTo(Instant.parse("2026-10-25T01:00:00Z"));
        // The repeated 02:00 wall-clock hour exists twice and must not be collapsed into one slot.
        assertThat(slots.stream().map(slot -> slot.startsAt().toInstant()).distinct().count()).isEqualTo(25);
        assertThat(isContiguous(slots)).isTrue();
        assertThat(Duration.between(slots.get(0).startsAt(), slots.get(24).endsAt()).toHours()).isEqualTo(25);
    }

    @Test
    void keepsEveryHourOfASpringForwardDayWithoutInventingTheSkippedHour() {
        // 2026-03-29 has 23 real hours: 01:00 at +01:00 is followed directly by 03:00 at +02:00.
        List<PriceSlot> slots = fetchOsloDay(PriceArea.NO1, LocalDate.of(2026, 3, 29), "2026-03-28T23:00:00Z", 23);

        assertThat(slots).hasSize(23);
        // 01:00+01:00 is 00:00Z and the next provider hour, 03:00+02:00, is 01:00Z: no gap, no skipped hour.
        assertThat(slots.get(1).startsAt().toInstant()).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"));
        assertThat(slots.get(2).startsAt().toInstant()).isEqualTo(Instant.parse("2026-03-29T01:00:00Z"));
        assertThat(slots.get(2).startsAt().toInstant()).isEqualTo(slots.get(1).endsAt().toInstant());
        assertThat(isContiguous(slots)).isTrue();
        assertThat(Duration.between(slots.get(0).startsAt(), slots.get(22).endsAt()).toHours()).isEqualTo(23);
    }

    private List<PriceSlot> fetchOsloDay(PriceArea area, LocalDate date, String firstStartUtc, int hours) {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        StringBuilder json = new StringBuilder("[");
        Instant cursor = Instant.parse(firstStartUtc);
        for (int hour = 0; hour < hours; hour++) {
            if (hour > 0) {
                json.append(',');
            }
            Instant next = cursor.plus(Duration.ofHours(1));
            json.append("""
                    {"NOK_per_kWh":0.5,"EUR_per_kWh":0.05,"EXR":10.0,
                     "time_start":"%s","time_end":"%s"}
                    """.formatted(OffsetDateTime.ofInstant(cursor, oslo), OffsetDateTime.ofInstant(next, oslo)));
            cursor = next;
        }
        json.append(']');
        server.expect(requestTo(BASE_URL + "/prices/%d/%02d-%02d_%s.json".formatted(
                        date.getYear(), date.getMonthValue(), date.getDayOfMonth(), area.name())))
                .andRespond(withSuccess(json.toString(), MediaType.APPLICATION_JSON));
        return client.fetchDailyPrices(area, date);
    }

    private static boolean isContiguous(List<PriceSlot> slots) {
        for (int i = 1; i < slots.size(); i++) {
            if (!slots.get(i).startsAt().toInstant().equals(slots.get(i - 1).endsAt().toInstant())) {
                return false;
            }
        }
        return true;
    }

    @Test
    void emptyArrayReturnsAnEmptyListRatherThanThrowing() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3))).isEmpty();
    }

    @Test
    void notFoundMeansPricesAreNotPublishedYet() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3)))
                .isInstanceOf(PricesNotYetPublishedException.class);
    }

    @Test
    void serverErrorMapsToAProviderExceptionButNotNotYetPublished() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3)))
                .isInstanceOf(ElectricityPriceProviderException.class)
                .isNotInstanceOf(PricesNotYetPublishedException.class);
    }

    @Test
    void readTimeoutMapsToAProviderException() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3)))
                .isInstanceOf(ElectricityPriceProviderException.class);
    }

    @Test
    void unparseableBodyMapsToAProviderException() {
        server.expect(requestTo(BASE_URL + "/prices/2026/09-03_NO1.json"))
                .andRespond(withSuccess("{ this is not the expected array", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchDailyPrices(PriceArea.NO1, LocalDate.of(2026, 9, 3)))
                .isInstanceOf(ElectricityPriceProviderException.class);
    }
}
