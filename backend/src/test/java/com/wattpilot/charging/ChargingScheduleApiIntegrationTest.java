package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.service.ChargingScheduleService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code POST /charging-schedules} against a real PostgreSQL instance: the preview → confirm
 * flow, the single transaction over plan + slots + schedule, the 409 cases (stale pick, EV overlap),
 * and that only the confirmed candidate is stored with server-recomputed figures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChargingScheduleApiIntegrationTest {

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
    private ChargingScheduleService chargingScheduleService;

    @Autowired
    private ChargingPlanRepository chargingPlanRepository;

    @MockitoSpyBean
    private ChargingPlanSlotRepository chargingPlanSlotRepository;

    @Autowired
    private ChargingScheduleRepository chargingScheduleRepository;

    @Test
    void confirmingASecondCheapestCandidateStoresOnlyThatPlanSlotsAndSchedule() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1,
                "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = windowStart.plusHours(7);

        String preview = preview(token, evId, deadline);
        // Deliberately pick the 2nd-cheapest candidate to prove the others are not persisted.
        String selectedStartAt = JsonPath.read(preview, "$.candidates[1].recommendedStartAt");
        String selectedEndAt = JsonPath.read(preview, "$.candidates[1].recommendedEndAt");
        BigDecimal previewCost = decimalAt(preview, "$.candidates[1].estimatedCostNok");
        BigDecimal previewSavings = decimalAt(preview, "$.candidates[1].expectedSavingsNok");

        String body = mockMvc.perform(scheduleRequest(token, evId, deadline, selectedStartAt, selectedEndAt))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/charging-schedules/")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.evId").value((int) evId))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        long planId = ((Number) JsonPath.read(body, "$.planId")).longValue();
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        // Prices did not change between preview and confirm, so the server figures equal the preview's.
        assertThat(decimalAt(body, "$.estimatedCostNok")).isEqualByComparingTo(previewCost);
        assertThat(decimalAt(body, "$.expectedSavingsNok")).isEqualByComparingTo(previewSavings);

        assertThat(countPlansFor(userId)).isEqualTo(1);
        assertThat(countSchedulesFor(userId)).isEqualTo(1);
        assertThat(chargingPlanSlotRepository.findByChargingPlanIdOrderBySequenceNoAsc(planId)).hasSize(2);

        ChargingPlan plan = chargingPlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(ChargingPlanStatus.SUCCEEDED);
        assertThat(plan.getEstimatedCostNok()).isEqualByComparingTo(previewCost); // server value, not sent by client
        assertThat(plan.getRecommendedStartAt().toInstant())
                .isEqualTo(OffsetDateTime.parse(selectedStartAt).toInstant());

        mockMvc.perform(get("/api/v1/charging-schedules/" + scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) scheduleId));
        mockMvc.perform(get("/api/v1/charging-schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(scheduleId)).isNotEmpty());
        mockMvc.perform(get("/api/v1/charging-plans/" + planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(2));
    }

    @Test
    void aWindowThatIsNotACurrentCandidateIsRejectedWithConflict() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1,
                "0.90", "0.90", "0.30", "0.20", "0.90", "0.90");
        OffsetDateTime deadline = windowStart.plusHours(6);

        String preview = preview(token, evId, deadline);
        // Shift a real candidate window by 7 minutes: no breakpoint lands there, so it will not match.
        String selectedStartAt = OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedStartAt"))
                .plusMinutes(7).toString();
        String selectedEndAt = OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedEndAt"))
                .plusMinutes(7).toString();

        mockMvc.perform(scheduleRequest(token, evId, deadline, selectedStartAt, selectedEndAt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGING_CANDIDATE_UNAVAILABLE"));

        assertThat(countPlansFor(userId)).isZero();
    }

    @Test
    void aSecondScheduleOverlappingAnExistingOneForTheSameEvIsRejected() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1,
                "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90");
        OffsetDateTime deadline = windowStart.plusHours(7);

        String preview = preview(token, evId, deadline);
        String startAt = JsonPath.read(preview, "$.candidates[0].recommendedStartAt");
        String endAt = JsonPath.read(preview, "$.candidates[0].recommendedEndAt");

        mockMvc.perform(scheduleRequest(token, evId, deadline, startAt, endAt))
                .andExpect(status().isCreated());

        // Prices are unchanged, so the same window is still a candidate; booking it again overlaps.
        mockMvc.perform(scheduleRequest(token, evId, deadline, startAt, endAt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGING_SCHEDULE_CONFLICT"));

        assertThat(countPlansFor(userId)).isEqualTo(1);
        assertThat(countSchedulesFor(userId)).isEqualTo(1);
    }

    @Test
    void schedulingAgainstAnotherUsersEvIsNotFound() throws Exception {
        String owner = signUpAndToken();
        long evId = createEv(owner);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        String preview = preview(owner, evId, windowStart.plusHours(6));
        String stranger = signUpAndToken();

        mockMvc.perform(scheduleRequest(stranger, evId, windowStart.plusHours(6),
                        JsonPath.read(preview, "$.candidates[0].recommendedStartAt"),
                        JsonPath.read(preview, "$.candidates[0].recommendedEndAt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EV_NOT_FOUND"));
    }

    @Test
    void anInfeasibleRequestReturns422AndStoresNothing() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        // No prices seeded for NO3.
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusHours(8).truncatedTo(ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/charging-schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO3",
                                 "selectedStartAt":"%s","selectedEndAt":"%s"}
                                """.formatted(evId, deadline,
                                deadline.minusHours(4), deadline.minusHours(2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHARGING_PRICE_DATA_INSUFFICIENT"));

        assertThat(countPlansFor(userId)).isZero();
        assertThat(countSchedulesFor(userId)).isZero();
    }

    @Test
    void aSlotPersistenceFailureRollsBackTheWholeConfirmation() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20", "0.60");

        String preview = preview(token, evId, windowStart.plusHours(6));
        OffsetDateTime selectedStart = OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedStartAt"));
        OffsetDateTime selectedEnd = OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedEndAt"));

        org.mockito.Mockito.doThrow(new RuntimeException("simulated slot insert failure"))
                .when(chargingPlanSlotRepository).saveAll(org.mockito.ArgumentMatchers.any());

        CreateChargingScheduleRequest request = new CreateChargingScheduleRequest(evId,
                new BigDecimal("20"), new BigDecimal("50"), windowStart.plusHours(6), PriceArea.NO1,
                selectedStart, selectedEnd);

        assertThatThrownBy(() -> chargingScheduleService.createSchedule(userId, request))
                .isInstanceOf(RuntimeException.class);

        assertThat(countPlansFor(userId)).isZero();
        assertThat(countSchedulesFor(userId)).isZero();
    }

    private long countPlansFor(long userId) {
        return chargingPlanRepository.findAll().stream()
                .filter(plan -> plan.getUserId().equals(userId))
                .count();
    }

    private long countSchedulesFor(long userId) {
        List<Long> planIds = chargingPlanRepository.findIdsByUserId(userId);
        return chargingScheduleRepository.findAll().stream()
                .filter(schedule -> planIds.contains(schedule.getChargingPlanId()))
                .count();
    }

    private String preview(String token, long evId, OffsetDateTime deadline) throws Exception {
        return mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1"}
                                """.formatted(evId, deadline)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.RequestBuilder scheduleRequest(
            String token, long evId, OffsetDateTime deadline, String selectedStartAt, String selectedEndAt) {
        return post("/api/v1/charging-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                         "requiredCompletionAt":"%s","priceArea":"NO1",
                         "selectedStartAt":"%s","selectedEndAt":"%s"}
                        """.formatted(evId, deadline, selectedStartAt, selectedEndAt));
    }

    private static BigDecimal decimalAt(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    private OffsetDateTime seedHourlyPrices(PriceArea area, String... pricesPerKwh) {
        // Start at the current hour so "charge right now" (the baseline) is itself priced.
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        List<PriceSlot> slots = new ArrayList<>();
        OffsetDateTime cursor = windowStart;
        for (String price : pricesPerKwh) {
            slots.add(new PriceSlot(cursor, cursor.plusHours(1), new BigDecimal(price), "NOK"));
            cursor = cursor.plusHours(1);
        }
        electricityPriceService.importPrices(PriceProvider.HVA_KOSTER_STROMMEN, area, slots);
        return windowStart;
    }

    private long createEv(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/evs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Plan car","manufacturer":"BMW","model":"i4","batteryCapacityKwh":60,
                                 "maxAcChargingPowerKw":11,"defaultChargerPowerKw":10}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long currentUserId(String token) throws Exception {
        String me = mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(me, "$.id")).longValue();
    }

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted("schedule-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
