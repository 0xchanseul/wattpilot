package com.wattpilot.charging.controller;

import com.wattpilot.charging.service.ChargingPlanPreviewService;
import com.wattpilot.charging.service.ChargingPlanService;
import com.wattpilot.charging.service.ChargingScheduleService;
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
 * Request validation only; the filter chain is disabled because ownership, recalculation and
 * persistence are covered end to end by the API integration tests.
 */
@WebMvcTest({ChargingPlanController.class, ChargingScheduleController.class})
@AutoConfigureMockMvc(addFilters = false)
class ChargingControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChargingPlanPreviewService previewService;

    @MockitoBean
    private ChargingPlanService chargingPlanService;

    @MockitoBean
    private ChargingScheduleService chargingScheduleService;

    @Test
    void previewRejectsAMissingEvId() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentBatteryPercent":30,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-09-04T07:00:00+02:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("evId"));
    }

    @Test
    void previewRejectsATargetBatteryPercentAbove100() throws Exception {
        mockMvc.perform(post("/api/v1/charging-plans/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":30,"targetBatteryPercent":120,
                                 "requiredCompletionAt":"2026-09-04T07:00:00+02:00","priceArea":"NO1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("targetBatteryPercent"));
    }

    @Test
    void scheduleRejectsAMissingSelectedWindow() throws Exception {
        mockMvc.perform(post("/api/v1/charging-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":30,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-09-04T07:00:00+02:00","priceArea":"NO1",
                                 "selectedStartAt":"2026-09-04T00:00:00+02:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("selectedEndAt"));
    }

    @Test
    void scheduleRejectsAMissingPriceArea() throws Exception {
        mockMvc.perform(post("/api/v1/charging-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evId":1,"currentBatteryPercent":30,"targetBatteryPercent":80,
                                 "requiredCompletionAt":"2026-09-04T07:00:00+02:00",
                                 "selectedStartAt":"2026-09-04T00:00:00+02:00",
                                 "selectedEndAt":"2026-09-04T04:00:00+02:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priceArea"));
    }
}
