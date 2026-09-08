package com.wattpilot.history;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.entity.ChargingFailureCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the charging-history endpoints against a real PostgreSQL instance: the list projection +
 * summary, the terminal-status filter (WAITING / still-charging schedules excluded), planned vs.
 * realized figures, the EV-name snapshot, the "receipt" detail with its planned per-hour breakdown,
 * and 404 semantics.
 *
 * <p>The background scheduler is disabled and every Mock Charging outcome is stubbed on the
 * {@link ChargingExecutionPort} spy, so the test is independent of the local {@code .env} failure
 * injection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.charging.execution.enabled=false")
class ChargingHistoryApiIntegrationTest {

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
    void listsTerminalSessionsNewestFirstWithRealizedSavingsAndSummary() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long completedEvId = createEv(token, "Completed car");
        long completedScheduleId = runToCompletion(token, completedEvId, deadline);
        long completedSessionId = sessionId(completedScheduleId);

        long failedEvId = createEv(token, "Failed car");
        long failedScheduleId = runToStartFailure(token, failedEvId, deadline,
                ChargingFailureCode.CHARGER_UNAVAILABLE, "Charger did not respond");
        long failedSessionId = sessionId(failedScheduleId);

        long waitingEvId = createEv(token, "Waiting car");
        long waitingScheduleId = confirmSchedule(token, waitingEvId, deadline);

        String body = mockMvc.perform(get("/api/v1/charging-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sessionId").value((int) failedSessionId))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].evName").value("Failed car"))
                .andExpect(jsonPath("$.content[0].failureCode").value("CHARGER_UNAVAILABLE"))
                .andExpect(jsonPath("$.content[0].baselineCostNok").value(nullValue()))
                .andExpect(jsonPath("$.content[0].optimizedCostNok").value(nullValue()))
                .andExpect(jsonPath("$.content[0].estimatedSavingsNok").value(nullValue()))
                .andExpect(jsonPath("$.content[0].actualCostNok").value(nullValue()))
                .andExpect(jsonPath("$.content[0].realizedSavingsNok").value(nullValue()))
                .andExpect(jsonPath("$.content[1].sessionId").value((int) completedSessionId))
                .andExpect(jsonPath("$.content[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].evName").value("Completed car"))
                .andExpect(jsonPath("$.content[1].baselineCostNok").exists())
                .andExpect(jsonPath("$.content[1].optimizedCostNok").exists())
                .andExpect(jsonPath("$.content[1].estimatedSavingsNok").exists())
                .andExpect(jsonPath("$.content[1].actualEnergyKwh").exists())
                .andExpect(jsonPath("$.content[1].actualCostNok").exists())
                .andExpect(jsonPath("$.content[1].realizedSavingsNok").exists())
                .andExpect(jsonPath("$.content[1].failureCode").value(nullValue()))
                .andExpect(jsonPath("$.summary.totalSessions").value(2))
                .andExpect(jsonPath("$.summary.successRate").value(50.0))
                .andExpect(jsonPath("$.content[?(@.scheduleId == %d)]".formatted(waitingScheduleId)).isEmpty())
                .andReturn().getResponse().getContentAsString();

        // realizedSavings = baselineCostNok - actualCostNok; estimatedSavings = baselineCostNok - optimizedCostNok.
        BigDecimal baseline = decimal(body, "$.content[1].baselineCostNok");
        BigDecimal actualCost = decimal(body, "$.content[1].actualCostNok");
        BigDecimal optimizedCost = decimal(body, "$.content[1].optimizedCostNok");
        assertThat(decimal(body, "$.content[1].realizedSavingsNok"))
                .isEqualByComparingTo(baseline.subtract(actualCost));
        assertThat(decimal(body, "$.content[1].estimatedSavingsNok"))
                .isEqualByComparingTo(baseline.subtract(optimizedCost));

        // Summary totals are the realized figures of the one completed session.
        assertThat(decimal(body, "$.summary.totalEnergyKwh"))
                .isEqualByComparingTo(decimal(body, "$.content[1].actualEnergyKwh"));
        assertThat(decimal(body, "$.summary.totalSavingsNok"))
                .isEqualByComparingTo(decimal(body, "$.content[1].realizedSavingsNok"));
    }

    @Test
    void filtersByStatusAndEvAndKeepsTheSummaryScopeLevel() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long completedEvId = createEv(token, "Completed car");
        runToCompletion(token, completedEvId, deadline);

        long failedEvId = createEv(token, "Failed car");
        runToStartFailure(token, failedEvId, deadline, ChargingFailureCode.START_REJECTED, "Start command rejected");

        // status=FAILED narrows the list but the summary still covers both sessions.
        mockMvc.perform(get("/api/v1/charging-history").param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.summary.totalSessions").value(2))
                .andExpect(jsonPath("$.summary.successRate").value(50.0));

        // A valid session status that is never part of history matches nothing, summary unchanged.
        mockMvc.perform(get("/api/v1/charging-history").param("status", "STARTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.summary.totalSessions").value(2));

        // evId scopes both the list and the summary.
        mockMvc.perform(get("/api/v1/charging-history").param("evId", String.valueOf(completedEvId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.totalSessions").value(1))
                .andExpect(jsonPath("$.summary.successRate").value(100.0));

        String strangerToken = signUpAndToken();
        mockMvc.perform(get("/api/v1/charging-history").header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.summary.totalSessions").value(0))
                .andExpect(jsonPath("$.summary.successRate").value(0.0));
    }

    @Test
    void evNameIsTheConfirmationSnapshotAndDoesNotChangeWhenTheEvIsRenamed() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.30", "0.20", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(6);

        long evId = createEv(token, "Original name");
        long scheduleId = runToCompletion(token, evId, deadline);

        mockMvc.perform(patch("/api/v1/evs/" + evId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed later\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/charging-history").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].evName").value("Original name"));

        mockMvc.perform(get("/api/v1/charging-history/" + sessionId(scheduleId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evSnapshot.name").value("Original name"));
    }

    @Test
    void detailIsAReceiptWithConditionsPlannedBreakdownAndRealizedOutcome() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long evId = createEv(token, "Receipt car");
        long scheduleId = runToCompletion(token, evId, deadline);
        long sessionId = sessionId(scheduleId);

        String body = mockMvc.perform(get("/api/v1/charging-history/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value((int) sessionId))
                .andExpect(jsonPath("$.scheduleId").value((int) scheduleId))
                .andExpect(jsonPath("$.evSnapshot.model").value("i4"))
                .andExpect(jsonPath("$.startBatteryPercent").value(20))
                .andExpect(jsonPath("$.targetBatteryPercent").value(50))
                .andExpect(jsonPath("$.priceArea").value("NO1"))
                .andExpect(jsonPath("$.recommendedStartAt").exists())
                .andExpect(jsonPath("$.recommendedEndAt").exists())
                .andExpect(jsonPath("$.plannedEnergyKwh").exists())
                .andExpect(jsonPath("$.optimizedCostNok").exists())
                .andExpect(jsonPath("$.baselineCostNok").exists())
                .andExpect(jsonPath("$.estimatedSavingsNok").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualEnergyKwh").exists())
                .andExpect(jsonPath("$.actualCostNok").exists())
                .andExpect(jsonPath("$.realizedSavingsNok").exists())
                .andExpect(jsonPath("$.plannedSlots").isArray())
                .andExpect(jsonPath("$.plannedSlots[0].pricePerKwh").exists())
                .andExpect(jsonPath("$.plannedSlots[0].plannedEnergyKwh").exists())
                .andExpect(jsonPath("$.plannedSlots[0].expectedCostNok").exists())
                .andReturn().getResponse().getContentAsString();

        // Planned slot energy sums to the plan's grid-side total; realized savings = baseline - actual.
        List<Object> slotEnergies = JsonPath.read(body, "$.plannedSlots[*].plannedEnergyKwh");
        BigDecimal slotSum = slotEnergies.stream().map(v -> new BigDecimal(v.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(slotSum).isEqualByComparingTo(decimal(body, "$.plannedEnergyKwh"));
        assertThat(decimal(body, "$.realizedSavingsNok"))
                .isEqualByComparingTo(decimal(body, "$.baselineCostNok").subtract(decimal(body, "$.actualCostNok")));
    }

    @Test
    void failedDetailKeepsThePlanButHasNoRealizedFigures() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.30", "0.20", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(5);

        long evId = createEv(token, "Failed detail car");
        long scheduleId = runToStartFailure(token, evId, deadline,
                ChargingFailureCode.VEHICLE_DISCONNECTED, "Vehicle was not connected");

        mockMvc.perform(get("/api/v1/charging-history/" + sessionId(scheduleId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("VEHICLE_DISCONNECTED"))
                .andExpect(jsonPath("$.failureReason").value("Vehicle was not connected"))
                // The plan still succeeded, so the recommendation and its breakdown are present.
                .andExpect(jsonPath("$.recommendedStartAt").exists())
                .andExpect(jsonPath("$.baselineCostNok").exists())
                .andExpect(jsonPath("$.estimatedSavingsNok").exists())
                .andExpect(jsonPath("$.plannedSlots.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                // ...but nothing was realized.
                .andExpect(jsonPath("$.startedAt").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.actualEnergyKwh").value(nullValue()))
                .andExpect(jsonPath("$.actualCostNok").value(nullValue()))
                .andExpect(jsonPath("$.realizedSavingsNok").value(nullValue()));
    }

    @Test
    void detailReturns404ForUnknownStillRunningOrAnotherUsersSession() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.30", "0.20", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(4);

        mockMvc.perform(get("/api/v1/charging-history/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGING_HISTORY_NOT_FOUND"));

        // A schedule that has started but not finished has a STARTED session — not history.
        long evId = createEv(token, "Running car");
        long scheduleId = confirmSchedule(token, evId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(scheduleId));
        executionService.attemptStart(scheduleId);
        mockMvc.perform(get("/api/v1/charging-history/" + sessionId(scheduleId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // Another user's completed session is 404, not 403.
        String owner = signUpAndToken();
        long ownerEvId = createEv(owner, "Owner car");
        long ownerScheduleId = runToCompletion(owner, ownerEvId, deadline);
        mockMvc.perform(get("/api/v1/charging-history/" + sessionId(ownerScheduleId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private long runToCompletion(String token, long evId, OffsetDateTime deadline) throws Exception {
        long scheduleId = confirmSchedule(token, evId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(scheduleId));
        doReturn(ExecutionOutcome.success()).when(executionPort).complete(eq(scheduleId));
        executionService.attemptStart(scheduleId);
        executionService.attemptComplete(scheduleId);
        return scheduleId;
    }

    private long runToStartFailure(String token, long evId, OffsetDateTime deadline,
                                   ChargingFailureCode code, String reason) throws Exception {
        long scheduleId = confirmSchedule(token, evId, deadline);
        doReturn(ExecutionOutcome.failure(code, reason)).when(executionPort).start(eq(scheduleId));
        executionService.attemptStart(scheduleId);
        return scheduleId;
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
                                """.formatted("history-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
