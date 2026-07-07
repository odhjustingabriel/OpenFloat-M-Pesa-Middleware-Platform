package com.openfloat.mpesa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 * <p>
 * Accessible at:
 * <ul>
 *   <li>Swagger UI: /swagger</li>
 *   <li>OpenAPI JSON: /openapi.json</li>
 * </ul>
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenFloat M-Pesa Middleware API")
                        .description("Enterprise-grade middleware for Safaricom M-Pesa Daraja API integration. "
                                + "Provides unified REST APIs for STK Push, C2B, B2C, Reversals, "
                                + "transaction management, reconciliation, and audit logging.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("OpenFloat Engineering")
                                .email("engineering@openfloat.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Development"),
                        new Server().url("https://api.openfloat.com").description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer Token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
