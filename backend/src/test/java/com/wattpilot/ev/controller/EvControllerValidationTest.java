package com.wattpilot.ev.controller;

import com.wattpilot.ev.service.EvService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request validation only; the filter chain is disabled because ownership and authorization are
 * covered end to end by {@code EvApiIntegrationTest}.
 */
@WebMvcTest(EvController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvService evService;

    @Test
    void createRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/v1/evs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","manufacturer":"BMW","model":"i4","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void createRejectsANonPositiveBatteryCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/evs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"i4","manufacturer":"BMW","model":"i4","batteryCapacityKwh":0,"maxAcChargingPowerKw":11,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("batteryCapacityKwh"));
    }

    @Test
    void createRejectsAcChargingPowerAboveTheGridLimit() throws Exception {
        mockMvc.perform(post("/api/v1/evs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"i4","manufacturer":"BMW","model":"i4","batteryCapacityKwh":81.1,"maxAcChargingPowerKw":23,"defaultChargerPowerKw":7.4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("maxAcChargingPowerKw"));
    }

    @Test
    void updateRejectsAPayloadWithNoFields() throws Exception {
        mockMvc.perform(patch("/api/v1/evs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
