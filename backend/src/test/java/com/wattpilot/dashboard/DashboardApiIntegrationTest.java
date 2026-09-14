package com.wattpilot.dashboard;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import com.wattpilot.charging.repository.ChargingSessionRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code GET /dashboard} against a real PostgreSQL instance: an account with no history gets
 * an all-zero, 30-day zero-filled payload; a populated account gets realized summary/cost-comparison
 * figures, the nearest active schedule as {@code nextCharging}, the current hour's price, and the last
 * completed session in {@code recentSessions}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.charging.execution.enabled=false")
class DashboardApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElectricityPriceService electricityPriceService;

    @Autowired
    private ChargingExecutionService executionService;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @MockitoSpyBean
    private ChargingExecutionPort executionPort;

    @Test
    void anAccountWithNoDataGetsAZeroedThirtyDayDashboard() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSessions").value(0))
                .andExpect(jsonPath("$.summary.totalEnergyKwh").value(0))
                .andExpect(jsonPath("$.summary.totalSavingsNok").value(0))
                .andExpect(jsonPath("$.summary.averageCostPerKwh").value(0))
                .andExpect(jsonPath("$.nextCharging").value(nullValue()))
                .andExpect(jsonPath("$.currentPrice").value(nullValue()))
                .andExpect(jsonPath("$.savingsTrend.length()").value(30))
                .andExpect(jsonPath("$.savingsTrend[0].savingsNok").value(0))
                .andExpect(jsonPath("$.savingsTrend[29].savingsNok").value(0))
                .andExpect(jsonPath("$.costComparison.baselineCostNok").value(0))
                .andExpect(jsonPath("$.costComparison.savingsPercent").value(0))
                .andExpect(jsonPath("$.recentSessions.length()").value(0));
    }

    @Test
    void aggregatesCompletedSessionsAlongsideANextChargingReservationAndTheCurrentPrice() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long completedEvId = createEv(token, "Completed car");
        long completedScheduleId = confirmSchedule(token, completedEvId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(completedScheduleId));
        doReturn(ExecutionOutcome.success()).when(executionPort).complete(eq(completedScheduleId));
        executionService.attemptStart(completedScheduleId);
        executionService.attemptComplete(completedScheduleId);
        long completedSessionId = sessionId(completedScheduleId);

        long waitingEvId = createEv(token, "Waiting car");
        long waitingScheduleId = confirmSchedule(token, waitingEvId, deadline);

        String body = mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSessions").value(1))
                .andExpect(jsonPath("$.nextCharging.scheduleId").value((int) waitingScheduleId))
                .andExpect(jsonPath("$.nextCharging.evName").value("Waiting car"))
                .andExpect(jsonPath("$.nextCharging.status").value("WAITING"))
                .andExpect(jsonPath("$.currentPrice.priceNokPerKwh").value(0.90))
                .andExpect(jsonPath("$.recentSessions.length()").value(1))
                .andExpect(jsonPath("$.recentSessions[0].sessionId").value((int) completedSessionId))
                .andExpect(jsonPath("$.recentSessions[0].evName").value("Completed car"))
                .andExpect(jsonPath("$.savingsTrend.length()").value(30))
                .andReturn().getResponse().getContentAsString();

        // realizedSavings = baselineCostNok - actualCostNok, consistently across summary, recentSessions
        // and costComparison.
        BigDecimal recentSavings = decimal(body, "$.recentSessions[0].realizedSavingsNok");
        assertThat(decimal(body, "$.summary.totalSavingsNok")).isEqualByComparingTo(recentSavings);
        assertThat(decimal(body, "$.costComparison.savingsNok")).isEqualByComparingTo(recentSavings);

        // The savings landed on exactly one Oslo calendar day within the 30-day trend window; the trend
        // sums back to the same total regardless of which day that was.
        List<Object> trend = JsonPath.read(body, "$.savingsTrend[*].savingsNok");
        BigDecimal trendTotal = trend.stream()
                .map(value -> new BigDecimal(value.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(trendTotal).isEqualByComparingTo(recentSavings);

        // A different account sees none of this.
        String strangerToken = signUpAndToken();
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSessions").value(0))
                .andExpect(jsonPath("$.nextCharging").value(nullValue()))
                .andExpect(jsonPath("$.recentSessions.length()").value(0));
    }

    private long sessionId(long scheduleId) {
        ChargingSession session = sessionRepository.findByChargingScheduleId(scheduleId).orElseThrow();
        return session.getId();
    }

    private static BigDecimal decimal(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
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
                                """.formatted("dashboard-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
