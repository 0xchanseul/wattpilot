package com.wattpilot.charging;

import com.wattpilot.charging.entity.ChargingFailureCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockChargingPropertiesTest {

    @Test
    void bindsFlatPropertyKeysAsAScheduleIdToFailureCodeMap() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.charging.execution.mock.failures.12", "CHARGER_UNAVAILABLE")
                .withProperty("wattpilot.charging.execution.mock.failures.15", "SYSTEM_ERROR");

        MockChargingProperties properties = bind(environment);

        assertThat(properties.failures()).containsExactlyInAnyOrderEntriesOf(Map.of(
                12L, ChargingFailureCode.CHARGER_UNAVAILABLE,
                15L, ChargingFailureCode.SYSTEM_ERROR));
    }

    @Test
    void defaultsToAnEmptyMapWhenNothingIsConfigured() {
        assertThat(bind(new MockEnvironment()).failures()).isEmpty();
    }

    @Test
    void rejectsMissedExecutionWindowInjection() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("wattpilot.charging.execution.mock.failures.1", "MISSED_EXECUTION_WINDOW");

        assertThatThrownBy(() -> bind(environment))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static MockChargingProperties bind(MockEnvironment environment) {
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate("wattpilot.charging.execution.mock", MockChargingProperties.class);
    }
}
