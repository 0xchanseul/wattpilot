package com.wattpilot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a {@link Clock} so components that need the current time can depend on it instead of
 * calling {@code now()} statically, which keeps time-sensitive logic (e.g. the price-collection
 * scheduler working out "tomorrow" in Oslo) deterministic under test.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
