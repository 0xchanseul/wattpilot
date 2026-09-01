package com.wattpilot.ev.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wattpilot.ev.entity.EvStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Matches the {@code UpdateEvRequest} schema in docs/openapi.yaml.
 *
 * <p>Every field is optional. A JSON null and an omitted field are indistinguishable in a record,
 * and no field is nullable in the contract, so both mean "leave the stored value unchanged". Per the
 * contract's {@code minProperties: 1}, at least one field must be supplied. Setting {@code status}
 * to {@code ACTIVE} reactivates a previously deactivated EV.
 */
public record UpdateEvRequest(
        @Size(min = 1, max = 100) String name,
        @Size(min = 1, max = 100) String manufacturer,
        @Size(min = 1, max = 100) String model,
        @Positive @Digits(integer = 6, fraction = 2) BigDecimal batteryCapacityKwh,
        @Positive @DecimalMax("22.00") @Digits(integer = 6, fraction = 2) BigDecimal maxAcChargingPowerKw,
        @Positive @DecimalMax("22.00") @Digits(integer = 6, fraction = 2) BigDecimal defaultChargerPowerKw,
        EvStatus status
) {

    @JsonIgnore
    @AssertTrue(message = "at least one field must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return name != null || manufacturer != null || model != null || batteryCapacityKwh != null
                || maxAcChargingPowerKw != null || defaultChargerPowerKw != null || status != null;
    }
}
