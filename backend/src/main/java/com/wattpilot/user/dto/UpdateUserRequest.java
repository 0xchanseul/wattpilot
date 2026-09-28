package com.wattpilot.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wattpilot.common.PriceArea;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Matches the {@code UpdateUserRequest} schema in docs/openapi.yaml.
 *
 * <p>Every field is optional. A JSON null and an omitted field are indistinguishable in a record,
 * and no field is nullable in the contract, so both mean "leave the stored value unchanged". Per
 * the contract's {@code minProperties: 1}, at least one field must be supplied.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100) String name,
        PriceArea defaultPriceArea
) {

    @JsonIgnore
    @AssertTrue(message = "at least one field must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return name != null || defaultPriceArea != null;
    }
}
