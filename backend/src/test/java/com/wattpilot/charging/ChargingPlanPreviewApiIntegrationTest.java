package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code POST /charging-plans/preview} against a real PostgreSQL instance: candidate
 * ranking, the three-candidate cap, the infeasible mapping, and — critically — that a preview writes
 * nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChargingPlanPreviewApiIntegrationTest {

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
    private ChargingPlanRepository chargingPlanRepository;

    @Autowired
    private ChargingPlanSlotRepository chargingPlanSlotRepository;

    @Autowired
    private ChargingScheduleRepository chargingScheduleRepository;

    @Test
    void previewReturnsUpToThreeCostRankedCandidatesAndPersistsNothing() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        // A clear two-hour price dip a couple of hours out; "now" sits in an expensive hour.
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1,
                "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90");

        String body = mockMvc.perform(previewRequest(token, evId, 20, 50, windowStart.plusHours(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evId").value((int) evId))
                .andExpect(jsonPath("$.calculatedEnergyKwh").value(18.0))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(120))
                .andExpect(jsonPath("$.effectiveChargingPowerKw").value(10.0))
                .andExpect(jsonPath("$.candidates.length()").value(3))
                .andExpect(jsonPath("$.candidates[0].rank").value(1))
                .andExpect(jsonPath("$.candidates[2].rank").value(3))
                .andExpect(jsonPath("$.candidates[0].slots.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        List<BigDecimal> costs = readDecimals(body, "$.candidates[*].estimatedCostNok");
        assertThat(costs).isSortedAccordingTo(BigDecimal::compareTo);
        BigDecimal topCost = new BigDecimal(JsonPath.read(body, "$.candidates[0].estimatedCostNok").toString());
        BigDecimal topBaseline = new BigDecimal(JsonPath.read(body, "$.candidates[0].baselineCostNok").toString());
        BigDecimal topSavings = new BigDecimal(JsonPath.read(body, "$.candidates[0].expectedSavingsNok").toString());
        assertThat(topSavings).isEqualByComparingTo(topBaseline.subtract(topCost));
        assertThat(topSavings.signum()).isPositive();

        assertThat(chargingPlanRepository.count()).isZero();
        assertThat(chargingPlanSlotRepository.count()).isZero();
        assertThat(chargingScheduleRepository.count()).isZero();
    }

    @SuppressWarnings("unchecked")
    private static List<BigDecimal> readDecimals(String json, String path) {
        return ((List<Object>) JsonPath.read(json, path)).stream()
                .map(value -> new BigDecimal(value.toString()))
                .toList();
    }

    @Test
    void previewReturnsFewerThanThreeWhenTheWindowIsTight() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO2, "0.30", "0.20", "0.40");

        // 120-minute charge, 3-hour window -> only two feasible starts.
        mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO2"}
                                """.formatted(evId, windowStart.plusHours(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(2));
    }

    @Test
    void previewWithoutPriceDataReturns422() throws Exception {
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusHours(8).truncatedTo(ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO5"}
                                """.formatted(evId, deadline)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHARGING_PRICE_DATA_INSUFFICIENT"));

        assertThat(chargingPlanRepository.count()).isZero();
    }

    @Test
    void previewForAnotherUsersEvIsNotFound() throws Exception {
        String owner = signUpAndToken();
        long evId = createEv(owner);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        String stranger = signUpAndToken();

        mockMvc.perform(previewRequest(stranger, evId, 20, 50, windowStart.plusHours(6)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EV_NOT_FOUND"));
    }

    @Test
    void previewRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"2026-12-04T07:00:00+01:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder previewRequest(
            String token, long evId, int current, int target, OffsetDateTime deadline) {
        return post("/api/v1/charging-plans/preview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"evId":%d,"currentBatteryPercent":%d,"targetBatteryPercent":%d,
                         "requiredCompletionAt":"%s","priceArea":"NO1"}
                        """.formatted(evId, current, target, deadline));
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

    private String signUpAndToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wattpilot-secret","name":"Iris","defaultPriceArea":"NO1"}
                                """.formatted("preview-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
