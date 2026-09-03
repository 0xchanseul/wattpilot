package com.wattpilot.charging.controller;

import com.wattpilot.charging.service.ChargingPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request validation only; the filter chain is disabled because ownership, the SUCCEEDED/FAILED
 * outcome and persistence are covered end to end by {@code ChargingPlanApiIntegrationTest}.
 */
@WebMvcTest(ChargingPlanController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChargingPlanControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChargingPlanService chargingPlanService;

    @Test
    void rejectsAMissingEvId() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentBatteryPercent":25,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-08-24T07:00:00+02:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("evId"));
    }

    @Test
    void rejectsATargetBatteryPercentAbove100() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":25,"targetBatteryPercent":120,
                                 "requiredCompletionAt":"2026-08-24T07:00:00+02:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("targetBatteryPercent"));
    }

    @Test
    void rejectsANegativeCurrentBatteryPercent() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":-5,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-08-24T07:00:00+02:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentBatteryPercent"));
    }

    @Test
    void rejectsAMissingPriceArea() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":25,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-08-24T07:00:00+02:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priceArea"));
    }
}
