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
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
