package com.wattpilot.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Browser origins allowed to call the API.
 *
 * <p>Empty by default: only environments that actually serve a browser client from a
 * different origin (such as the local Vite dev server) opt in.
 */
@ConfigurationProperties(prefix = "wattpilot.security.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
