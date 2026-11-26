package com.warehouse.arrivalindex.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Warehouse Live Arrival Index API")
                        .version("1.0.0")
                        .description("Backend-only production system managing warehouse package IDs arriving in arbitrary order, "
                                + "maintaining a dynamically updated collection of sorted, non-overlapping consecutive intervals "
                                + "using an incremental TreeMap index.")
                        .contact(new Contact()
                                .name("Warehouse Architecture Team")
                                .email("backend-eng@warehouse.internal"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
