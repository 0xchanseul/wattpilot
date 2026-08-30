package com.wattpilot.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Base springdoc-openapi setup that exposes Swagger UI and the runtime OpenAPI
 * document. Detailed per-endpoint documentation is added alongside each API as it
 * is implemented; docs/openapi.yaml remains the design-time contract.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI wattPilotOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("WattPilot API")
                .version("v1")
                .description("Runtime API documentation for the WattPilot backend."));
    }
}
