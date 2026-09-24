package com.wattpilot.savings;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import com.wattpilot.charging.service.ChargingExecutionService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Exercises {@code GET /savings/summary} and {@code GET /savings/daily} against a real PostgreSQL
 * instance: a completed session lands in the caller-chosen date range with realized savings, an
 * {@code evId} filter scopes both endpoints, {@code granularity=MONTHLY} rolls the day up into its
 * calendar month, and an inverted range is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.charging.execution.enabled=false")
class SavingsApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElectricityPriceService electricityPriceService;

    @Autowired
    private ChargingExecutionService executionService;

    @MockitoSpyBean
    private ChargingExecutionPort executionPort;

    @Test
    void summaryAndDailyReportRealizedSavingsForACompletedSessionInRange() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long evId = createEv(token, "Reporting car");
        long scheduleId = confirmSchedule(token, evId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(scheduleId));
        doReturn(ExecutionOutcome.success()).when(executionPort).complete(eq(scheduleId));
        executionService.attemptStart(scheduleId);
        executionService.attemptComplete(scheduleId);

        LocalDate today = OffsetDateTime.now(ZoneOffset.UTC).atZoneSameInstant(OSLO).toLocalDate();
        LocalDate from = today.minusDays(1);
        LocalDate to = today.plusDays(1);

        String summaryBody = mockMvc.perform(get("/api/v1/savings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessionCount").value(1))
                .andExpect(jsonPath("$.currency").value("NOK"))
                .andReturn().getResponse().getContentAsString();
        BigDecimal totalSavings = new BigDecimal(JsonPath.read(summaryBody, "$.totalSavingsNok").toString());

        // Daily trend sums back to the same realized total over the same range.
        String dailyBody = mockMvc.perform(get("/api/v1/savings/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        List<Object> dailySavings = JsonPath.read(dailyBody, "$[*].savingsNok");
        BigDecimal dailyTotal = dailySavings.stream()
                .map(v -> new BigDecimal(v.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        org.assertj.core.api.Assertions.assertThat(dailyTotal).isEqualByComparingTo(totalSavings);

        // A range that does not cover today reports nothing.
        mockMvc.perform(get("/api/v1/savings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", today.minusDays(30).toString())
                        .param("to", today.minusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessionCount").value(0))
                .andExpect(jsonPath("$.totalSavingsNok").value(0));

        // An EV the caller does not own (here: any id no session belongs to) scopes to nothing, not an error.
        mockMvc.perform(get("/api/v1/savings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("evId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessionCount").value(0));

        // The same range, scoped to the EV that actually charged, still reports it.
        mockMvc.perform(get("/api/v1/savings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("evId", String.valueOf(evId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessionCount").value(1));

        // MONTHLY granularity rolls the same session up into a single point for the current month.
        LocalDate monthStart = today.withDayOfMonth(1);
        mockMvc.perform(get("/api/v1/savings/daily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", monthStart.toString())
                        .param("to", monthStart.plusMonths(1).minusDays(1).toString())
                        .param("granularity", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(monthStart.toString()))
                .andExpect(jsonPath("$[0].sessionCount").value(1));
    }

    @Test
    void invertedRangeIsRejectedAsAValidationError() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/savings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private long confirmSchedule(String token, long evId, OffsetDateTime deadline) throws Exception {
        String preview = mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1"}
                                """.formatted(evId, deadline)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String selectedStartAt = JsonPath.read(preview, "$.candidates[0].recommendedStartAt");
        String selectedEndAt = JsonPath.read(preview, "$.candidates[0].recommendedEndAt");

        String body = mockMvc.perform(post("/api/v1/charging-schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1",
                                 "selectedStartAt":"%s","selectedEndAt":"%s"}
                                """.formatted(evId, deadline, selectedStartAt, selectedEndAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private OffsetDateTime currentHour() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
    }

    private void seedHourlyPrices(PriceArea area, String... pricesPerKwh) {
        OffsetDateTime cursor = currentHour();
        List<PriceSlot> slots = new ArrayList<>();
        for (String price : pricesPerKwh) {
            slots.add(new PriceSlot(cursor, cursor.plusHours(1), new BigDecimal(price), "NOK"));
            cursor = cursor.plusHours(1);
        }
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, area, slots);
    }

    private long createEv(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/evs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","manufacturer":"BMW","model":"i4","batteryCapacityKwh":60,
                                 "maxAcChargingPowerKw":11,"defaultChargerPowerKw":10}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted("savings-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
