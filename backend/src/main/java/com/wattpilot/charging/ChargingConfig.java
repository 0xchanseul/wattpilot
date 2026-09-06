package com.wattpilot.charging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link ChargingProperties}, {@link ChargingExecutionProperties} and
 * {@link MockChargingProperties} so the charging-efficiency constant, the execution scheduler's
 * timing/retry settings, and the Mock Charging failure-injection map come from configuration rather
 * than being hardcoded.
 */
@Configuration
@EnableConfigurationProperties({
        ChargingProperties.class,
        ChargingExecutionProperties.class,
        MockChargingProperties.class
})
public class ChargingConfig {
}
