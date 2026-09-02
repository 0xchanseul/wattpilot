package com.wattpilot.integration.hvakosterstrommen;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds the {@link RestClient} used only by {@link HvaKosterStrommenClient}: base URL and timeouts
 * come from {@link HvaKosterStrommenProperties} so the external endpoint is never hardcoded and an
 * unreachable provider fails fast instead of hanging a scheduler thread.
 */
@Configuration
@EnableConfigurationProperties(HvaKosterStrommenProperties.class)
public class HvaKosterStrommenClientConfig {

    @Bean
    RestClient hvaKosterStrommenRestClient(HvaKosterStrommenProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
