package com.wattpilot.charging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * System-level charging constants.
 *
 * @param efficiency fraction of grid energy that reaches the battery. V1 uses a single system-wide
 *                   value and does not store it per EV. It only lengthens the estimated charging
 *                   duration; the billed energy and every cost are computed on the full charger draw.
 */
@ConfigurationProperties("wattpilot.charging")
public record ChargingProperties(
        @DefaultValue("0.9") BigDecimal efficiency
) {
}
