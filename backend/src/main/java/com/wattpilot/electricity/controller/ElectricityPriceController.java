package com.wattpilot.electricity.controller;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.ElectricityPriceListResponse;
import com.wattpilot.electricity.dto.ElectricityPriceResponse;
import com.wattpilot.electricity.service.ElectricityPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/electricity-prices")
@Tag(name = "Electricity Price", description = "Norwegian hourly electricity prices")
public class ElectricityPriceController {

    private final ElectricityPriceService electricityPriceService;

    public ElectricityPriceController(ElectricityPriceService electricityPriceService) {
        this.electricityPriceService = electricityPriceService;
    }

    @Operation(summary = "Get hourly electricity prices")
    @GetMapping
    public ResponseEntity<ElectricityPriceListResponse> listElectricityPrices(
            @RequestParam("priceArea") PriceArea priceArea,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(electricityPriceService.getPrices(priceArea, from, to));
    }

    @Operation(summary = "Get the current price")
    @GetMapping("/latest")
    public ResponseEntity<ElectricityPriceResponse> getLatestElectricityPrice(
            @RequestParam("priceArea") PriceArea priceArea) {
        return ResponseEntity.ok(electricityPriceService.getCurrentPrice(priceArea));
    }
}
