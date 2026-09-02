package com.wattpilot.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling and binds scheduler settings. Individual scheduler beans are conditional,
 * so when a job is disabled this only registers an idle task scheduler.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PriceCollectionProperties.class)
public class SchedulingConfig {
}
