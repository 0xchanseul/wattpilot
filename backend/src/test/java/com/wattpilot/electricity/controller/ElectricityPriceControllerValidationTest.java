package com.wattpilot.electricity.controller;

import com.wattpilot.electricity.service.ElectricityPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Query-parameter binding only; authorization is covered end to end by
 * {@code ElectricityPriceApiIntegrationTest}.
 */
@WebMvcTest(ElectricityPriceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ElectricityPriceControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElectricityPriceService electricityPriceService;

    @Test
    void listRejectsAMissingFromParameter() throws Exception {
        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO1")
                        .param("to", "2026-08-25T00:00:00+02:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void listRejectsAnUnknownPriceArea() throws Exception {
        mockMvc.perform(get("/api/v1/electricity-prices")
                        .param("priceArea", "NO9")
                        .param("from", "2026-08-24T00:00:00+02:00")
                        .param("to", "2026-08-25T00:00:00+02:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void latestRejectsAMissingPriceArea() throws Exception {
        mockMvc.perform(get("/api/v1/electricity-prices/latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
