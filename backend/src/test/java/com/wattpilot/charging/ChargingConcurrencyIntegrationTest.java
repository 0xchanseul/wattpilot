package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.dto.CreateChargingScheduleRequest;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.charging.service.ChargingExecutionService;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.service.ElectricityPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that the row locks in the charging flow actually serialise competing requests, using real
 * threads released together by a latch against a real PostgreSQL instance. A mocked repository could
 * not show this: the guarantee comes from {@code SELECT ... FOR UPDATE}, not from Java code.
 *
 * <p>Each scenario fires several callers at the same schedule or EV at once and asserts that exactly
 * one of them wins and that the database holds exactly one resulting row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "wattpilot.charging.execution.enabled=false",
        "wattpilot.charging.execution.mock.random-failure-enabled=false"
})
@Import(ChargingExecutionApiIntegrationTest.ClockConfig.class)
class ChargingConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-09-05T00:00:00Z");
    private static final int PARALLEL_CALLERS = 6;

    @Autowired private MockMvc mockMvc;
    @Autowired private ElectricityPriceService electricityPriceService;
    @Autowired private ChargingScheduleService chargingScheduleService;
    @Autowired private ChargingExecutionService executionService;
    @Autowired private ChargingPlanRepository planRepository;
    @Autowired private ChargingScheduleRepository scheduleRepository;
    @Autowired private ChargingSessionRepository sessionRepository;
    @Autowired private Clock clock;

    @Test
    void concurrentCreationOfOverlappingSchedulesForOneEvProducesExactlyOne() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        CreateChargingScheduleRequest request = previewAndBuildRequest(token, evId, windowStart.plusHours(4));

        List<String> outcomes = runConcurrently(PARALLEL_CALLERS, () -> {
            try {
                chargingScheduleService.createSchedule(userId, request);
                return "created";
            } catch (BusinessException e) {
                return "rejected:" + e.errorCode();
            }
        });

        assertThat(outcomes.stream().filter("created"::equals).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(("rejected:" + ErrorCode.CHARGING_SCHEDULE_CONFLICT)::equals).count())
                .isEqualTo(PARALLEL_CALLERS - 1);

        List<Long> planIds = planRepository.findIdsByUserIdAndEvId(userId, evId);
        assertThat(planIds).hasSize(1);
        assertThat(scheduleRepository.findAll().stream()
                .filter(schedule -> planIds.contains(schedule.getChargingPlanId()))).hasSize(1);
    }

    @Test
    void concurrentCancellationsOfOneScheduleResolveToExactlyOneCancellation() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        long scheduleId = createScheduleViaApi(token, evId, windowStart.plusHours(4));

        List<String> outcomes = runConcurrently(PARALLEL_CALLERS, () -> {
            try {
                chargingScheduleService.cancelSchedule(userId, scheduleId);
                return "cancelled";
            } catch (BusinessException e) {
                return "rejected:" + e.errorCode();
            }
        });

        assertThat(outcomes.stream().filter("cancelled"::equals).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(("rejected:" + ErrorCode.CHARGING_SCHEDULE_NOT_CANCELLABLE)::equals).count())
                .isEqualTo(PARALLEL_CALLERS - 1);
        assertThat(scheduleRepository.findById(scheduleId).orElseThrow().getStatus())
                .isEqualTo(ChargingScheduleStatus.CANCELLED);
    }

    @Test
    void concurrentStartAttemptsOnOneScheduleStartItExactlyOnce() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        long scheduleId = createScheduleViaApi(token, evId, windowStart.plusHours(4));
        ChargingSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        setClock(schedule.getScheduledStartAt().toInstant());

        runConcurrently(PARALLEL_CALLERS, () -> {
            executionService.attemptStart(scheduleId);
            return "attempted";
        });

        assertThat(scheduleRepository.findById(scheduleId).orElseThrow().getStatus())
                .isEqualTo(ChargingScheduleStatus.IN_PROGRESS);
        Optional<ChargingSession> session = sessionRepository.findByChargingScheduleId(scheduleId);
        assertThat(session).isPresent();
        assertThat(sessionRepository.findAll().stream()
                .filter(candidate -> candidate.getChargingScheduleId().equals(scheduleId))).hasSize(1);
    }

    /**
     * Runs {@code task} on {@code callers} threads that are all released by one latch, so they hit the
     * database at the same moment instead of drifting into a sequential run.
     */
    private static <T> List<T> runConcurrently(int callers, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }

    private void setClock(Instant instant) {
        ((ChargingExecutionApiIntegrationTest.MutableClock) clock).set(instant);
    }

    private CreateChargingScheduleRequest previewAndBuildRequest(String token, long evId, OffsetDateTime deadline)
            throws Exception {
        String preview = mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1"}
                                """.formatted(evId, deadline)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new CreateChargingScheduleRequest(
                evId,
                new BigDecimal("20"),
                new BigDecimal("50"),
                deadline,
                PriceArea.NO1,
                OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedStartAt")),
                OffsetDateTime.parse(JsonPath.read(preview, "$.candidates[0].recommendedEndAt")));
    }

    private long createScheduleViaApi(String token, long evId, OffsetDateTime deadline) throws Exception {
        CreateChargingScheduleRequest request = previewAndBuildRequest(token, evId, deadline);
        String body = mockMvc.perform(post("/api/v1/charging-schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1",
                                 "selectedStartAt":"%s","selectedEndAt":"%s"}
                                """.formatted(evId, deadline, request.selectedStartAt(), request.selectedEndAt())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private OffsetDateTime seedHourlyPrices(PriceArea area, String... pricesPerKwh) {
        OffsetDateTime windowStart = OffsetDateTime.ofInstant(INITIAL_INSTANT, ZoneOffset.UTC);
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
                                {"name":"Race car","manufacturer":"BMW","model":"i4","batteryCapacityKwh":60,
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
                                """.formatted("race-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
