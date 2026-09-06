package com.wattpilot.charging;

import com.jayway.jsonpath.JsonPath;
import com.wattpilot.charging.entity.ChargingFailureCode;
import com.wattpilot.charging.entity.ChargingSchedule;
import com.wattpilot.charging.entity.ChargingScheduleStatus;
import com.wattpilot.charging.entity.ChargingSession;
import com.wattpilot.charging.entity.ChargingSessionStatus;
import com.wattpilot.charging.port.ChargingExecutionPort;
import com.wattpilot.charging.port.ExecutionOutcome;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.charging.service.ChargingExecutionService;
import com.wattpilot.charging.service.ChargingScheduleService;
import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.entity.PriceProvider;
import com.wattpilot.electricity.service.ElectricityPriceService;
import com.wattpilot.scheduler.ChargingExecutionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the charging-execution scheduler end to end against a real PostgreSQL instance: normal
 * start/complete, a missed window, cancellation (including a race against a concurrent start attempt),
 * a business execution failure, transient-error retry exhaustion, and the charging_sessions CHECK/unique
 * constraints. Time is driven by a {@link MutableClock} test bean so the scheduler's read-state queries
 * can be exercised deterministically without waiting on real wall-clock minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "wattpilot.charging.execution.enabled=true")
@Import(ChargingExecutionApiIntegrationTest.ClockConfig.class)
class ChargingExecutionApiIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-09-05T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ElectricityPriceService electricityPriceService;
    @Autowired private ChargingScheduleService chargingScheduleService;
    @Autowired private ChargingScheduleRepository scheduleRepository;
    @Autowired private ChargingSessionRepository sessionRepository;
    @Autowired private ChargingExecutionService executionService;
    @Autowired private ChargingExecutionScheduler executionScheduler;
    @Autowired private Clock clock;

    @MockitoSpyBean private ChargingExecutionPort executionPort;

    @Test
    void startsAndCompletesAWaitingScheduleAcrossTicks() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1,
                "0.90", "0.90", "0.30", "0.20", "0.90", "0.90", "0.90");

        String body = confirmSchedule(token, evId, windowStart.plusHours(7));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledStartAt"));
        OffsetDateTime scheduledEndAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledEndAt"));

        setClock(scheduledStartAt.toInstant());
        executionScheduler.runOnce();

        mockMvc.perform(get("/api/v1/charging-schedules/" + scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.session.status").value("STARTED"))
                .andExpect(jsonPath("$.session.startedAt").exists());

        ChargingSchedule plannedSchedule = scheduleRepository.findById(scheduleId).orElseThrow();
        BigDecimal expectedEnergyKwh = plannedSchedule.getExpectedEnergyKwh();
        BigDecimal expectedCostNok = plannedSchedule.getEstimatedCostNok();

        setClock(scheduledEndAt.toInstant());
        executionScheduler.runOnce();

        mockMvc.perform(get("/api/v1/charging-schedules/" + scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.session.status").value("COMPLETED"))
                .andExpect(jsonPath("$.session.actualEnergyKwh").value(expectedEnergyKwh.doubleValue()))
                .andExpect(jsonPath("$.session.actualCostNok").value(expectedCostNok.doubleValue()))
                .andExpect(jsonPath("$.session.estimatedSavingsNok").exists());
    }

    @Test
    void missesAWaitingScheduleWhoseWindowClosesBeforeItStarts() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");

        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledEndAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledEndAt"));

        // Never run the scheduler while WAITING is still startable; jump straight past the window close.
        setClock(scheduledEndAt.plusMinutes(1).toInstant());
        executionScheduler.runOnce();

        ChargingSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        ChargingSession session = sessionRepository.findByChargingScheduleId(scheduleId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ChargingSessionStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo(ChargingFailureCode.MISSED_EXECUTION_WINDOW);
        assertThat(session.getStartedAt()).isNull();
    }

    @Test
    void cancelsAWaitingScheduleAndRejectsARepeatCancelOrLateStart() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");

        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledStartAt"));

        mockMvc.perform(post("/api/v1/charging-schedules/" + scheduleId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/charging-schedules/" + scheduleId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGING_SCHEDULE_NOT_CANCELLABLE"));

        setClock(scheduledStartAt.toInstant());
        executionScheduler.runOnce();

        ChargingSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.CANCELLED);
        assertThat(sessionRepository.findByChargingScheduleId(scheduleId)).isEmpty();
    }

    @Test
    void aConcurrentStartAttemptAndCancelResolveToExactlyOneOutcome() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        long userId = currentUserId(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");

        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledStartAt"));
        setClock(scheduledStartAt.toInstant());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Void> startTask = () -> {
            ready.countDown();
            go.await();
            executionService.attemptStart(scheduleId);
            return null;
        };
        Callable<String> cancelTask = () -> {
            ready.countDown();
            go.await();
            try {
                chargingScheduleService.cancelSchedule(userId, scheduleId);
                return "cancelled";
            } catch (BusinessException e) {
                return "conflict:" + e.errorCode();
            }
        };

        Future<Void> startFuture = executor.submit(startTask);
        Future<String> cancelFuture = executor.submit(cancelTask);
        ready.await();
        go.countDown();
        startFuture.get(10, TimeUnit.SECONDS);
        String cancelResult = cancelFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        ChargingSchedule finalSchedule = scheduleRepository.findById(scheduleId).orElseThrow();
        Optional<ChargingSession> session = sessionRepository.findByChargingScheduleId(scheduleId);

        if (finalSchedule.getStatus() == ChargingScheduleStatus.IN_PROGRESS) {
            assertThat(session).isPresent();
            assertThat(session.get().getStatus()).isEqualTo(ChargingSessionStatus.STARTED);
            assertThat(cancelResult).isEqualTo("conflict:CHARGING_SCHEDULE_NOT_CANCELLABLE");
        } else {
            assertThat(finalSchedule.getStatus()).isEqualTo(ChargingScheduleStatus.CANCELLED);
            assertThat(session).isEmpty();
            assertThat(cancelResult).isEqualTo("cancelled");
        }
    }

    @Test
    void aBusinessExecutionFailureIsRecordedWithItsFailureReason() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");

        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledStartAt"));

        doReturn(ExecutionOutcome.failure(ChargingFailureCode.CHARGER_UNAVAILABLE, "Charger did not respond"))
                .when(executionPort).start(eq(scheduleId));

        setClock(scheduledStartAt.toInstant());
        executionScheduler.runOnce();

        ChargingSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(schedule.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        ChargingSession session = sessionRepository.findByChargingScheduleId(scheduleId).orElseThrow();
        assertThat(session.getFailureCode()).isEqualTo(ChargingFailureCode.CHARGER_UNAVAILABLE);
        assertThat(session.getFailureReason()).isEqualTo("Charger did not respond");
    }

    @Test
    void transientPortErrorsRetryWithBackoffThenFinalizeAsSystemError() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");

        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        OffsetDateTime scheduledStartAt = OffsetDateTime.parse(JsonPath.read(body, "$.scheduledStartAt"));

        doThrow(new RuntimeException("simulated transient error")).when(executionPort).start(eq(scheduleId));

        setClock(scheduledStartAt.toInstant());
        executionScheduler.runOnce();
        ChargingSchedule afterFirst = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ChargingScheduleStatus.WAITING);
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);
        assertThat(afterFirst.getNextRetryAt()).isNotNull();

        setClock(afterFirst.getNextRetryAt().toInstant());
        executionScheduler.runOnce();
        ChargingSchedule afterSecond = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(ChargingScheduleStatus.WAITING);
        assertThat(afterSecond.getRetryCount()).isEqualTo(2);

        setClock(afterSecond.getNextRetryAt().toInstant());
        executionScheduler.runOnce();
        ChargingSchedule afterThird = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(afterThird.getStatus()).isEqualTo(ChargingScheduleStatus.FAILED);
        ChargingSession session = sessionRepository.findByChargingScheduleId(scheduleId).orElseThrow();
        assertThat(session.getFailureCode()).isEqualTo(ChargingFailureCode.SYSTEM_ERROR);
        assertThat(session.getFailureReason()).doesNotContain("simulated transient error");
    }

    @Test
    void chargingSessionConstraintsRejectAnInvalidFieldCombinationAndADuplicateSchedule() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();

        ChargingSession startedWithOutcomeFields = ChargingSession.started(scheduleId, windowStart);
        ReflectionTestUtils.setField(startedWithOutcomeFields, "actualEnergyKwh", new BigDecimal("5.00"));
        assertThatThrownBy(() -> sessionRepository.saveAndFlush(startedWithOutcomeFields))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void chargingSessionUniqueConstraintRejectsASecondSessionForTheSameSchedule() throws Exception {
        setClock(INITIAL_INSTANT);
        String token = signUpAndToken();
        long evId = createEv(token);
        OffsetDateTime windowStart = seedHourlyPrices(PriceArea.NO1, "0.50", "0.40", "0.30", "0.20");
        String body = confirmSchedule(token, evId, windowStart.plusHours(4));
        long scheduleId = ((Number) JsonPath.read(body, "$.id")).longValue();

        sessionRepository.saveAndFlush(ChargingSession.started(scheduleId, windowStart));
        ChargingSession duplicate = ChargingSession.started(scheduleId, windowStart);
        assertThatThrownBy(() -> sessionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void setClock(Instant instant) {
        ((MutableClock) clock).set(instant);
    }

    private String confirmSchedule(String token, long evId, OffsetDateTime deadline) throws Exception {
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

        return mockMvc.perform(post("/api/v1/charging-schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":%d,"currentBatteryPercent":20,"targetBatteryPercent":50,
                                 "requiredCompletionAt":"%s","priceArea":"NO1",
                                 "selectedStartAt":"%s","selectedEndAt":"%s"}
                                """.formatted(evId, deadline, selectedStartAt, selectedEndAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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
                                {"name":"Exec car","manufacturer":"BMW","model":"i4","batteryCapacityKwh":60,
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
                                """.formatted("exec-user%d@example.com".formatted(EMAIL_SEQUENCE.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(INITIAL_INSTANT, ZoneOffset.UTC);
        }
    }

    /** A settable {@link Clock} so scheduler ticks can be driven to specific instants without waiting. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
