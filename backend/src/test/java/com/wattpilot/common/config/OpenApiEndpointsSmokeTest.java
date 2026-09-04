package com.wattpilot.common.config;

import com.wattpilot.auth.repository.RefreshTokenRepository;
import com.wattpilot.charging.repository.ChargingPlanRepository;
import com.wattpilot.charging.repository.ChargingPlanSlotRepository;
import com.wattpilot.charging.repository.ChargingScheduleRepository;
import com.wattpilot.electricity.repository.ElectricityPriceRepository;
import com.wattpilot.ev.repository.EvRepository;
import com.wattpilot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class OpenApiEndpointsSmokeTest {

    @Autowired
    MockMvc mockMvc;

    // The persistence layer is excluded above, so the repositories the services depend on are
    // stubbed; this test only cares that the OpenAPI document and Swagger UI are served.
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

    @Test
    void apiDocsExposesConfiguredInfo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("WattPilot API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    void apiDocsDeclaresBearerAuthAndExemptsThePublicAuthEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security").doesNotExist());
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
