package com.wattpilot.electricity;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.service.ElectricityPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the electricity-price endpoints against a real PostgreSQL instance: the Flyway schema,
 * the {@code price_provider} enum mapping, the {@code (provider, price_area, starts_at)} unique key
 * used for upserts, and the security filter chain are verified together. Price data is seeded through
 * {@link ElectricityPriceService#importPrices} because there is no public write endpoint in V1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ElectricityPriceApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElectricityPriceService electricityPriceService;

    @Test
    void listingPricesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO1")
                        .param("from", "2026-08-24T00:00:00+02:00")
                        .param("to", "2026-08-25T00:00:00+02:00"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void listReturnsTheAreaHoursInStartOrderRenderedInOsloTime() throws Exception {
        String token = signUpAndToken();
        OffsetDateTime firstHour = OffsetDateTime.parse("2026-08-24T00:00:00+02:00");
        seedConsecutiveHours(PriceArea.NO1, firstHour, "0.50", "0.40", "0.60");

        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO1")
                        .param("from", "2026-08-24T00:00:00+02:00")
                        .param("to", "2026-08-24T03:00:00+02:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceArea").value("NO1"))
                .andExpect(jsonPath("$.provider").value("HVA_KOSTER_STROMMEN"))
                .andExpect(jsonPath("$.currency").value("NOK"))
                .andExpect(jsonPath("$.prices.length()").value(3))
                .andExpect(jsonPath("$.prices[0].startsAt", containsString("+02:00")))
                .andExpect(jsonPath("$.prices[0].pricePerKwh").value(0.50))
                .andExpect(jsonPath("$.prices[1].pricePerKwh").value(0.40))
                .andExpect(jsonPath("$.prices[2].startsAt").value("2026-08-24T02:00:00+02:00"));
    }

    @Test
    void listIsScopedToTheRequestedAreaAndWindow() throws Exception {
        String token = signUpAndToken();
        OffsetDateTime firstHour = OffsetDateTime.parse("2026-09-10T00:00:00+02:00");
        seedConsecutiveHours(PriceArea.NO4, firstHour, "0.11", "0.12", "0.13");

        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO5")
                        .param("from", "2026-09-10T00:00:00+02:00")
                        .param("to", "2026-09-11T00:00:00+02:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.length()").value(0));

        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO4")
                        .param("from", "2026-09-10T01:00:00+02:00")
                        .param("to", "2026-09-10T02:00:00+02:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.length()").value(1))
                .andExpect(jsonPath("$.prices[0].pricePerKwh").value(0.12));
    }

    @Test
    void anInvertedWindowIsRejectedAsUnprocessable() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO1")
                        .param("from", "2026-08-25T00:00:00+02:00")
                        .param("to", "2026-08-24T00:00:00+02:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    void latestReturnsTheHourCoveringNowAnd404WhenThereIsNoData() throws Exception {
        String token = signUpAndToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO2, List.of(
                new PriceSlot(now.minusMinutes(20), now.plusMinutes(40), new BigDecimal("0.845"), "NOK")));

        mockMvc.perform(get("/api/v1/electricity-prices/latest")
                        .param("priceArea", "NO2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceArea").value("NO2"))
                .andExpect(jsonPath("$.pricePerKwh").value(0.845));

        mockMvc.perform(get("/api/v1/electricity-prices/latest")
                        .param("priceArea", "NO3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ELECTRICITY_PRICE_NOT_FOUND"));
    }

    @Test
    void reimportingAnHourUpdatesItInPlaceInsteadOfBreakingTheUniqueKey() throws Exception {
        String token = signUpAndToken();
        OffsetDateTime hourStart = OffsetDateTime.parse("2026-07-01T02:00:00+02:00");
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO3, List.of(
                new PriceSlot(hourStart, hourStart.plusHours(1), new BigDecimal("0.500000"), "NOK")));
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, PriceArea.NO3, List.of(
                new PriceSlot(hourStart, hourStart.plusHours(1), new BigDecimal("0.800000"), "NOK")));

        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO3")
                        .param("from", "2026-07-01T02:00:00+02:00")
                        .param("to", "2026-07-01T03:00:00+02:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.length()").value(1))
                .andExpect(jsonPath("$.prices[0].pricePerKwh").value(0.80));
    }

    private void seedConsecutiveHours(PriceArea area, OffsetDateTime firstHourStart, String... pricesPerKwh) {
        List<PriceSlot> slots = new ArrayList<>();
        OffsetDateTime cursor = firstHourStart;
        for (String price : pricesPerKwh) {
            slots.add(new PriceSlot(cursor, cursor.plusHours(1), new BigDecimal(price), "NOK"));
            cursor = cursor.plusHours(1);
        }
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, area, slots);
    }

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted(nextEmail())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String nextEmail() {
        return "price-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet());
    }
}
