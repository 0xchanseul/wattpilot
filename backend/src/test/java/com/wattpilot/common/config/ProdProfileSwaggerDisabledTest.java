package com.wattpilot.common.config;

import com.wattpilot.auth.repository.RefreshTokenRepository;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.charging.repository.ChargingSessionRepository;
import com.wattpilot.dashboard.repository.DashboardRepository;
import com.wattpilot.electricity.repository.ElectricityPriceRepository;
import com.wattpilot.ev.repository.EvRepository;
import com.wattpilot.history.repository.ChargingHistoryRepository;
import com.wattpilot.savings.repository.SavingsRepository;
import com.wattpilot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code prod} profile is the one environment where the API contract should not be publicly
 * browsable (see docs/deployment.md, "Swagger exposure"). Every other profile (local, cloud) keeps
 * Swagger UI on for development and for the temporary Azure test environment's SSH-tunnel access.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        // The readiness group lists "db", which does not exist without a DataSource.
        "management.endpoint.health.validate-group-membership=false",
        // application-prod.yml intentionally has no CORS default and no JWT secret of its own;
        // supply just enough to let the security filter chain start.
        "wattpilot.security.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2Q="
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProdProfileSwaggerDisabledTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    EvRepository evRepository;

    @MockitoBean
    ElectricityPriceRepository electricityPriceRepository;

    @MockitoBean
    ChargingPlanRepository chargingPlanRepository;

    @MockitoBean
    ChargingPlanSlotRepository chargingPlanSlotRepository;

    @MockitoBean
    ChargingScheduleRepository chargingScheduleRepository;

    @MockitoBean
    ChargingSessionRepository chargingSessionRepository;

    @MockitoBean
    ChargingHistoryRepository chargingHistoryRepository;

    @MockitoBean
    DashboardRepository dashboardRepository;

    @MockitoBean
    SavingsRepository savingsRepository;

    @Test
    void apiDocsIsNotServedInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotServedInProd() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealthStaysPubliclyAvailableInProd() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
