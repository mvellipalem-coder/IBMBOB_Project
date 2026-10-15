package com.example.ebookstore.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the approved API contract.
 *
 * <p>{@code openapi.yaml} is the source of truth for the API. We serve that exact file at
 * {@code /openapi.yaml} and Swagger UI ({@code /swagger-ui.html}) displays it, instead of generating a
 * different document from the Java code. This keeps the running API and the approved contract in sync.
 * The URL is public (see {@code SecurityConfig}).
 */
@RestController
public class OpenApiDocumentController {

    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    @GetMapping("/openapi.yaml")
    public ResponseEntity<Resource> contract() {
        return ResponseEntity.ok().contentType(YAML).body(new ClassPathResource("openapi.yaml"));
    }
}
