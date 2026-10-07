package com.wattpilot.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoPropertiesTest {

    @Test
    void defaultsToDisabledWithNoTemplateRequired() {
        DemoProperties properties = bind(new MockEnvironment());

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.maxActiveAccounts()).isEqualTo(200);
        assertThat(properties.cleanupBatchSize()).isEqualTo(200);
    }

    @Test
    void aNonPositiveCleanupBatchSizeIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.demo.cleanup-batch-size", "0");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledWithoutATemplateEmailIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.demo.enabled", "true")
                .withProperty("wattpilot.demo.template-email", " ");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledWithATemplateEmailBindsSuccessfully() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.demo.enabled", "true")
                .withProperty("wattpilot.demo.template-email", "demo@example.com")
                .withProperty("wattpilot.demo.ttl", "6h")
                .withProperty("wattpilot.demo.max-active-accounts", "50");

        DemoProperties properties = bind(environment);

        assertThat(properties.templateEmail()).isEqualTo("demo@example.com");
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(6));
        assertThat(properties.maxActiveAccounts()).isEqualTo(50);
    }

    @Test
    void aNonPositiveTtlIsRejected() {
        MockEnvironment environment = new MockEnvironment().withProperty("wattpilot.demo.ttl", "0s");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNonPositiveCapacityIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.demo.max-active-accounts", "0");

        assertThatThrownBy(() -> bind(environment)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static DemoProperties bind(MockEnvironment environment) {
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("wattpilot.demo", DemoProperties.class);
    }
}
