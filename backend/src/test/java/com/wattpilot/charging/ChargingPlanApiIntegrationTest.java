package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.dto.CreateChargingPlanRequest;
import com.wattpilot.charging.entity.ChargingPlan;
import com.wattpilot.charging.entity.ChargingPlanStatus;
import com.wattpilot.charging.exception.ChargingPlanInfeasibleException;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.service.ChargingPlanService;
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
 * Exercises {@code /charging-plans} against a real PostgreSQL instance: the V2 Flyway migration, the
 * charging_plans / charging_plan_slots mapping, ownership scoping, the SUCCEEDED/FAILED persistence
 * split, and the single transaction covering plan + slots are all verified together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChargingPlanApiIntegrationTest {

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
    private ChargingPlanService chargingPlanService;

    @Autowired
    private ChargingPlanRepository chargingPlanRepository;

    // Spied so one test can force a slot-insert failure; behaves as the real repository otherwise.
    @MockitoSpyBean
    private ChargingPlanSlotRepository chargingPlanSlotRepository;

    @Test
    void createReturnsASucceededPlanWithItsCheapestWindowAndSlots() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20", "0.60", "0.70");

        String body = mockMvc.perform(post("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(evId, 20, 50, windowStart, windowStart.plusHours(6))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/charging-plans/")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.evId").value((int) evId))
                .andExpect(jsonPath("$.priceArea").value("NO1"))
                .andExpect(jsonPath("$.evSnapshot.batteryCapacityKwh").value(60.0))
                .andExpect(jsonPath("$.effectiveChargingPowerKw").value(10.0))
                .andExpect(jsonPath("$.calculatedEnergyKwh").value(18.0))
                .andExpect(jsonPath("$.expectedEnergyKwh").value(20.0))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(120))
                .andExpect(jsonPath("$.estimatedCostNok").value(5.0))
                .andExpect(jsonPath("$.baselineCostNok").value(9.0))
                .andExpect(jsonPath("$.expectedSavingsNok").value(4.0))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].pricePerKwh").value(0.30))
                .andExpect(jsonPath("$.slots[0].plannedEnergyKwh").value(10.0))
                .andExpect(jsonPath("$.slots[0].expectedCostNok").value(3.0))
                .andReturn().getResponse().getContentAsString();

        long planId = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.recommendedStartAt")).toInstant())
                .isEqualTo(windowStart.plusHours(2).toInstant());

        // Retrievable by id and present in the list.
        mockMvc.perform(get("/api/v1/charging-plans/" + planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) planId))
                .andExpect(jsonPath("$.slots.length()").value(2));

        mockMvc.perform(get("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(planId)).isNotEmpty());
    }

    @Test
    void slotSumsStayConsistentWithThePlanAggregatesIncludingPartialSlots() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        // Cheap first two hours, expensive after: the cheapest 120-minute window starts 30 minutes in,
        // giving a partial leading slot and a partial trailing slot.
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.20", "0.20", "0.90", "0.90");

        String body = mockMvc.perform(post("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(evId, 20, 50,
                                windowStart.plusMinutes(30), windowStart.plusHours(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].plannedEnergyKwh").value(5.0))
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.slots[0].startsAt")).toInstant())
                .isEqualTo(windowStart.plusMinutes(30).toInstant());
        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.slots[0].endsAt")).toInstant())
                .isEqualTo(windowStart.plusHours(1).toInstant());

        BigDecimal expectedEnergy = new BigDecimal(JsonPath.read(body, "$.expectedEnergyKwh").toString());
        BigDecimal estimatedCost = new BigDecimal(JsonPath.read(body, "$.estimatedCostNok").toString());
        List<?> slots = JsonPath.read(body, "$.slots");
        BigDecimal energySum = BigDecimal.ZERO;
        BigDecimal costSum = BigDecimal.ZERO;
        for (int i = 0; i < slots.size(); i++) {
            energySum = energySum.add(new BigDecimal(JsonPath.read(body, "$.slots[%d].plannedEnergyKwh".formatted(i)).toString()));
            costSum = costSum.add(new BigDecimal(JsonPath.read(body, "$.slots[%d].expectedCostNok".formatted(i)).toString()));
        }
        assertThat(energySum).isEqualByComparingTo(expectedEnergy);
        assertThat(costSum).isEqualByComparingTo(estimatedCost);
    }

    @Test
    void anInfeasibleRequestPersistsAFailedPlanAndReturns422() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30");

        String body = mockMvc.perform(post("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(evId, 20, 50, windowStart, windowStart.plusMinutes(30))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHARGING_PLAN_INFEASIBLE"))
                .andExpect(jsonPath("$.planStatus").value("FAILED"))
                .andExpect(jsonPath("$.reasonCode").value("DEADLINE_TOO_SOON"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty())
                .andExpect(jsonPath("$.chargingPlanId").isNumber())
                .andReturn().getResponse().getContentAsString();

        long failedPlanId = ((Number) JsonPath.read(body, "$.chargingPlanId")).longValue();

        ChargingPlan stored = chargingPlanRepository.findById(failedPlanId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ChargingPlanStatus.FAILED);
        assertThat(stored.getFailureReason()).isNotBlank();
        assertThat(stored.getRecommendedStartAt()).isNull();
        assertThat(chargingPlanSlotRepository.findByChargingPlanIdOrderBySequenceNoAsc(failedPlanId)).isEmpty();

        // A FAILED plan id is never retrievable through the API.
        mockMvc.perform(get("/api/v1/charging-plans/" + failedPlanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGING_PLAN_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(failedPlanId)).isEmpty());
    }

    @Test
    void planningAgainstAnotherUsersEvIsNotFound() throws Exception {
        String owner = signUpAndToken();
        long evId = createEv(owner);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        String stranger = signUpAndToken();

        mockMvc.perform(post("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(evId, 20, 50, windowStart, windowStart.plusHours(6))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EV_NOT_FOUND"));
    }

    @Test
    void listingChargingPlansRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/charging-plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void aTargetNotAboveTheCurrentLevelIsRejected() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40");

        mockMvc.perform(post("/api/v1/charging-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(evId, 50, 50, windowStart, windowStart.plusHours(6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void aSlotPersistenceFailureRollsBackTheWholePlan() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20", "0.60");
        long userId = currentUserId(token);

        org.mockito.Mockito.doThrow(new RuntimeException("simulated slot insert failure"))
                .when(chargingPlanSlotRepository).saveAll(org.mockito.ArgumentMatchers.any());

        CreateChargingPlanRequest request = new CreateChargingPlanRequest(evId,
                new BigDecimal("20"), new BigDecimal("50"),
                windowStart.plusHours(6), windowStart, PriceArea.NO1);

        assertThatThrownBy(() -> chargingPlanService.createPlan(userId, request))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ChargingPlanInfeasibleException.class);

        assertThat(chargingPlanRepository.findAll().stream()
                .filter(plan -> plan.getUserId().equals(userId))
                .count())
                .isZero();
    }

    private long currentUserId(String token) throws Exception {
        String me = mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(me, "$.id")).longValue();
    }

    private String planRequest(long evId, int current, int target, OffsetDateTime earliestStartAt,
                               OffsetDateTime requiredCompletionAt) {
        return """
                {"evId":%d,"currentBatteryPercent":%d,"targetBatteryPercent":%d,
                 "earliestStartAt":"%s","requiredCompletionAt":"%s","priceArea":"NO1"}
                """.formatted(evId, current, target, earliestStartAt, requiredCompletionAt);
    }

    /**
     * Seeds consecutive hourly NO1 prices starting two hours from now (top of the hour, UTC) and
     * returns that first hour's start.
     */
    private OffsetDateTime seedHourlyPrices(PriceArea area, String... pricesPerKwh) {
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(2).truncatedTo(ChronoUnit.HOURS);
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

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted("plan-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
