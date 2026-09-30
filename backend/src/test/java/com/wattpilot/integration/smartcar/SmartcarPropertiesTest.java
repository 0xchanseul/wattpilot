package com.wattpilot.integration.smartcar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartcarPropertiesTest {

    @Test
    void defaultsToDisabledWithNoCredentialsRequired() {
        SmartcarProperties properties = bind(new MockEnvironment());

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.mode()).isEqualTo("simulated");
        assertThat(properties.vehicleApiBaseUrl()).isEqualTo("https://vehicle.api.smartcar.com/v3");
    }

    @Test
    void enabledWithoutClientIdIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.integration.smartcar.enabled", "true")
                .withProperty("wattpilot.integration.smartcar.client-secret", "secret")
                .withProperty("wattpilot.integration.smartcar.application-id", "app-id")
                .withProperty("wattpilot.integration.smartcar.redirect-uri", "https://example.com/callback");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledWithoutRedirectUriIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.integration.smartcar.enabled", "true")
                .withProperty("wattpilot.integration.smartcar.client-id", "client-id")
                .withProperty("wattpilot.integration.smartcar.client-secret", "secret")
                .withProperty("wattpilot.integration.smartcar.application-id", "app-id");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledWithAllCredentialsBindsSuccessfully() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.integration.smartcar.enabled", "true")
                .withProperty("wattpilot.integration.smartcar.client-id", "client-id")
                .withProperty("wattpilot.integration.smartcar.client-secret", "secret")
                .withProperty("wattpilot.integration.smartcar.application-id", "app-id")
                .withProperty("wattpilot.integration.smartcar.redirect-uri", "https://example.com/callback");

        SmartcarProperties properties = bind(environment);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.clientId()).isEqualTo("client-id");
    }

    private static SmartcarProperties bind(MockEnvironment environment) {
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("wattpilot.integration.smartcar", SmartcarProperties.class);
    }
}
