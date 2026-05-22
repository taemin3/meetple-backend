package com.meetple.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void openApiAddsBearerAuthSecurityScheme() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        SecurityScheme securityScheme = openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH_SCHEME);

        assertThat(securityScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(securityScheme.getScheme()).isEqualTo("bearer");
        assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");
    }
}
