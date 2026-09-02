package com.wattpilot.integration.hvakosterstrommen;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Connection settings for the Hva koster strømmen client. The API is public (no key, no auth), so
 * only the base URL and timeouts are configurable.
 *
 * @param baseUrl        API root, without a trailing slash
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    response read timeout
 */
@ConfigurationProperties("wattpilot.integration.hva-koster-strommen")
public record HvaKosterStrommenProperties(
        @DefaultValue("https://www.hvakosterstrommen.no/api/v1") String baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
}
