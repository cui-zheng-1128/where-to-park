package com.wheretopark.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. The spec is served at {@code /v3/api-docs} and the UI at {@code /swagger-ui.html}; it is the integration entry point for API consumers.
 */
@Configuration
public class OpenApiConfiguration {

    /**
     * Provides the OpenAPI document metadata (title, version, license note).
     *
     * @return the OpenAPI descriptor
     */
    @Bean
    public OpenAPI whereToParkOpenAPI() {
        return new OpenAPI().info(new Info()
            .title("where-to-park API")
            .version("v1")
            .description("Nearby parking discovery. Multi-city, multi-source. Read-only: only GET is mapped, every other method returns 405.")
            .license(new License().name("Data: per-source open licence, see the attribution block of each response")));
    }
}
