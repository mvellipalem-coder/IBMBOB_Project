package com.example.ebookstore.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StartupInfoLoggerTest {

    @Test
    void keepsHostPortAndDatabaseName() {
        assertThat(StartupInfoLogger.safeJdbcUrl("jdbc:postgresql://localhost:5432/ebookstore_db"))
                .isEqualTo("jdbc:postgresql://localhost:5432/ebookstore_db");
    }

    @Test
    void removesQueryStringThatMayContainAPassword() {
        assertThat(StartupInfoLogger.safeJdbcUrl("jdbc:postgresql://localhost:5432/ebookstore_db?user=a&password=s3cret"))
                .isEqualTo("jdbc:postgresql://localhost:5432/ebookstore_db")
                .doesNotContain("s3cret");
    }

    @Test
    void removesUserInfoFromTheAuthority() {
        assertThat(StartupInfoLogger.safeJdbcUrl("jdbc:postgresql://admin:s3cret@db.internal:5432/ebookstore_db"))
                .isEqualTo("jdbc:postgresql://db.internal:5432/ebookstore_db")
                .doesNotContain("s3cret");
    }

    @Test
    void handlesMissingUrl() {
        assertThat(StartupInfoLogger.safeJdbcUrl(null)).isEqualTo("(not set)");
        assertThat(StartupInfoLogger.safeJdbcUrl(" ")).isEqualTo("(not set)");
    }
}
