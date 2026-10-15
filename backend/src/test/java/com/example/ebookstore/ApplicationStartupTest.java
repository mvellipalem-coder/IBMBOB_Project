package com.example.ebookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Smoke test: the whole application starts against a real (embedded) PostgreSQL, Flyway runs the migrations,
 * and the configuration templates are complete. The values below are throw-away test values only.
 */
@SpringBootTest
class ApplicationStartupTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStartsAndMigrationsRan() {
        Long books = jdbcTemplate.queryForObject("SELECT count(*) FROM books", Long.class);
        assertThat(books).isEqualTo(26);
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
