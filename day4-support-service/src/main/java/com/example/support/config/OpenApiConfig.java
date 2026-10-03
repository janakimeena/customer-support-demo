package com.example.support.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Header of the generated OpenAPI document. springdoc builds the rest (paths, parameters, schemas, validation
 * constraints) from the controllers and DTOs, so the docs can't drift from the code.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI supportServiceOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI().info(new Info()
                .title("Customer Support API")
                .version("day4")
                .description("""
                        Training demo: customers and support tickets on PostgreSQL, plus customer ingestion from CSV, a partner API and a legacy database (%s).

                        Paged endpoints take `page` (0-based), `size` (max 100) and `sort` (e.g. `createdAt,desc`).
                        Errors are RFC 9457 problem details. Customer ids look like `C-100`.
                        """.formatted(applicationName)));
    }
}
