package com.wattpilot.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the admin settings. */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig {
}
