package com.wattpilot.visit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the visit counting settings. */
@Configuration
@EnableConfigurationProperties(VisitProperties.class)
public class VisitConfig {
}
