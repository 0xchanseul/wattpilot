package com.wattpilot.common.config;

import com.wattpilot.common.security.RefreshTokenCookieProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Base springdoc-openapi setup that exposes Swagger UI and the runtime OpenAPI
 * document. Detailed per-endpoint documentation is added alongside each API as it
 * is implemented; docs/openapi.yaml remains the design-time contract.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String REFRESH_COOKIE = "refreshCookie";

    @Bean
    public OpenAPI wattPilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WattPilot API")
                        .version("v1")
                        .description("Runtime API documentation for the WattPilot backend."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        // The opaque refresh token: read from an HttpOnly cookie by /auth/refresh
                        // and /auth/logout, never from a request body.
                        .addSecuritySchemes(REFRESH_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(RefreshTokenCookieProperties.COOKIE_NAME)))
                // Applied to every operation, matching docs/openapi.yaml; the public auth
                // endpoints opt out individually with @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
