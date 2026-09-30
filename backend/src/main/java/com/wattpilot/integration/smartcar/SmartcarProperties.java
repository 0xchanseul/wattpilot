package com.wattpilot.integration.smartcar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration for the read-only Smartcar vehicle-telemetry integration (V1.5).
 *
 * <p>Credentials are required only when {@code enabled} is true, so a deployment that never turns
 * this on (every profile today) needs no Smartcar account. See {@code SmartcarPropertiesTest} for
 * the validation this constructor performs.
 *
 * @param enabled          turns the feature on; when false, every vehicle-connection endpoint
 *                         responds with {@code VEHICLE_PROVIDER_NOT_CONFIGURED} (503)
 * @param clientId         Smartcar dashboard Client ID, used for the application access token
 * @param clientSecret     Smartcar dashboard Client Secret
 * @param applicationId    Smartcar dashboard Application ID, used to build the Connect URL
 *                         (a different value from {@code clientId})
 * @param redirectUri      URI Smartcar redirects back to after Connect; must be registered in the
 *                         Smartcar dashboard exactly as configured here
 * @param mode             Connect mode: {@code simulated} for the vehicle simulator (default, used
 *                         everywhere except a deliberate manual live test), or {@code live}
 * @param connectBaseUrl   base URL for building the Connect URL
 * @param authBaseUrl      base URL for the application access-token endpoint
 * @param vehicleApiBaseUrl base URL for signals/connections calls
 * @param connectTimeout   TCP connect timeout for Smartcar calls
 * @param readTimeout      response read timeout for Smartcar calls
 */
@ConfigurationProperties("wattpilot.integration.smartcar")
public record SmartcarProperties(
        @DefaultValue("false") boolean enabled,
        String clientId,
        String clientSecret,
        String applicationId,
        String redirectUri,
        @DefaultValue("simulated") String mode,
        @DefaultValue("https://connect.smartcar.com/oauth/authorize") String connectBaseUrl,
        @DefaultValue("https://iam.smartcar.com/oauth2/token") String authBaseUrl,
        @DefaultValue("https://vehicle.api.smartcar.com/v3") String vehicleApiBaseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
    public SmartcarProperties {
        if (enabled) {
            requireConfigured(clientId, "wattpilot.integration.smartcar.client-id");
            requireConfigured(clientSecret, "wattpilot.integration.smartcar.client-secret");
            requireConfigured(applicationId, "wattpilot.integration.smartcar.application-id");
            requireConfigured(redirectUri, "wattpilot.integration.smartcar.redirect-uri");
        }
    }

    private static void requireConfigured(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    property + " must be set when wattpilot.integration.smartcar.enabled=true");
        }
    }
}
