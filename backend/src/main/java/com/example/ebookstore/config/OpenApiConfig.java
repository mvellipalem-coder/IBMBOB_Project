package com.example.ebookstore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Publishes the approved API contract.
 *
 * <p>{@code openapi.yaml} is the source of truth for the API. We serve that exact file at
 * {@code /openapi.yaml} and Swagger UI ({@code /swagger-ui.html}) displays it, instead of generating a
 * different document from the Java code. This keeps the running API and the approved contract in sync.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi.yaml").addResourceLocations("classpath:/openapi.yaml");
    }
}
