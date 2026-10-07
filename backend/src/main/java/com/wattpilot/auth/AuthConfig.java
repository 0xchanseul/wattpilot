package com.wattpilot.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the demo login settings. */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class AuthConfig {
}
