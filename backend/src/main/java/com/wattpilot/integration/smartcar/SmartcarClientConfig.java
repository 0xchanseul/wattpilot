package com.wattpilot.integration.smartcar;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds the {@link RestClient} used only by {@link SmartcarClient}. A single client is enough
 * because all v3 vehicle-data/connections calls share the {@code vehicleApiBaseUrl}; the
 * application-token request goes to a different host ({@code authBaseUrl}) and is called with an
 * absolute URI instead of a second bean, since it is only one call.
 */
@Configuration
@EnableConfigurationProperties(SmartcarProperties.class)
public class SmartcarClientConfig {

    @Bean
    RestClient smartcarRestClient(SmartcarProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.vehicleApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
