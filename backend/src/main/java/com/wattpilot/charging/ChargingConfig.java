package com.wattpilot.charging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link ChargingProperties} so the charging-efficiency constant comes from configuration
 * rather than being hardcoded in the optimizer.
 */
@Configuration
@EnableConfigurationProperties(ChargingProperties.class)
public class ChargingConfig {
}
