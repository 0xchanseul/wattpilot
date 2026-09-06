package com.wattpilot.charging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link ChargingProperties} and {@link ChargingExecutionProperties} so the charging-efficiency
 * constant and the execution scheduler's timing/retry settings come from configuration rather than
 * being hardcoded.
 */
@Configuration
@EnableConfigurationProperties({ChargingProperties.class, ChargingExecutionProperties.class})
public class ChargingConfig {
}
