package com.example.ebookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** The {@code prod} profile must start, and must switch off Swagger UI. All values are throw-away test values. */
@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
        registry.add("CORS_ALLOWED_ORIGIN", () -> "https://shop.example.test");
    }

    @Autowired
    private Environment environment;

    @Test
    void productionHardeningIsApplied() {
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("app.cors.allowed-origin")).isEqualTo("https://shop.example.test");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
        }
    }
}
