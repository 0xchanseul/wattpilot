package com.wattpilot.charging;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code GET /charging-schedules} (the overview) against a real PostgreSQL instance:
 * WAITING/IN_PROGRESS/terminal schedules land in the right block, {@code recentActivity} is capped at
 * {@link com.wattpilot.charging.service.ChargingScheduleService#RECENT_ACTIVITY_LIMIT} newest-first,
 * and an account with no schedules gets empty blocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.charging.execution.enabled=false")
class ChargingSchedulesOverviewApiIntegrationTest {

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

    @MockitoSpyBean
    private ChargingExecutionPort executionPort;

    @Test
    void groupsUpcomingInProgressAndRecentActivity() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        long waitingEvId = createEv(token, "Waiting car");
        long waitingScheduleId = confirmSchedule(token, waitingEvId, deadline);

        long runningEvId = createEv(token, "Running car");
        long runningScheduleId = confirmSchedule(token, runningEvId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(runningScheduleId));
        executionService.attemptStart(runningScheduleId);

        long doneEvId = createEv(token, "Done car");
        long doneScheduleId = confirmSchedule(token, doneEvId, deadline);
        doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(doneScheduleId));
        doReturn(ExecutionOutcome.success()).when(executionPort).complete(eq(doneScheduleId));
        executionService.attemptStart(doneScheduleId);
        executionService.attemptComplete(doneScheduleId);

        mockMvc.perform(get("/api/v1/charging-schedules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].id").value((int) waitingScheduleId))
                .andExpect(jsonPath("$.upcoming[0].status").value("WAITING"))
                .andExpect(jsonPath("$.upcoming[0].slots").isArray())
                .andExpect(jsonPath("$.inProgress.length()").value(1))
                .andExpect(jsonPath("$.inProgress[0].id").value((int) runningScheduleId))
                .andExpect(jsonPath("$.inProgress[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.inProgress[0].session.status").value("STARTED"))
                .andExpect(jsonPath("$.recentActivity.length()").value(1))
                .andExpect(jsonPath("$.recentActivity[0].scheduleId").value((int) doneScheduleId))
                .andExpect(jsonPath("$.recentActivity[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.recentActivity[0].evName").value("Done car"))
                .andExpect(jsonPath("$.recentActivity[0].sessionId").exists())
                .andExpect(jsonPath("$.recentActivity[0].actualEnergyKwh").exists())
                // Recent activity is a slim view: no slot breakdown or cost picture here.
                .andExpect(jsonPath("$.recentActivity[0].slots").doesNotExist());
    }

    @Test
    void recentActivityIsCappedAtFiveNewestFirst() throws Exception {
        String token = signUpAndToken();
        seedHourlyPrices(PriceArea.NO1, "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = currentHour().plusHours(8);

        List<Long> completedScheduleIds = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            long evId = createEv(token, "Car " + i);
            long scheduleId = confirmSchedule(token, evId, deadline);
            doReturn(ExecutionOutcome.success()).when(executionPort).start(eq(scheduleId));
            doReturn(ExecutionOutcome.success()).when(executionPort).complete(eq(scheduleId));
            executionService.attemptStart(scheduleId);
            executionService.attemptComplete(scheduleId);
            completedScheduleIds.add(scheduleId);
        }

        mockMvc.perform(get("/api/v1/charging-schedules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentActivity.length()").value(5))
                // Newest first: the last schedule completed is at the top, the first is dropped.
                .andExpect(jsonPath("$.recentActivity[0].scheduleId").value(completedScheduleIds.get(5).intValue()))
                .andExpect(jsonPath("$.recentActivity[4].scheduleId").value(completedScheduleIds.get(1).intValue()))
                .andExpect(jsonPath("$.recentActivity[?(@.scheduleId == %d)]".formatted(completedScheduleIds.get(0)))
                        .isEmpty());
    }

    @Test
    void anAccountWithNoSchedulesGetsEmptyBlocks() throws Exception {
        String token = signUpAndToken();

        mockMvc.perform(get("/api/v1/charging-schedules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(0))
                .andExpect(jsonPath("$.inProgress.length()").value(0))
                .andExpect(jsonPath("$.recentActivity.length()").value(0));
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
                                """.formatted("overview-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
