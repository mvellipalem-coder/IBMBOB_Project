package com.example.ebookstore.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prints a short, safe summary once the application has started, so a beginner can see at a glance
 * which profile is active and whether the database connection and Flyway migrations worked.
 *
 * <p>Security: the password, the JWT secret and any {@code user:password@} or {@code ?password=} part of
 * the database URL are never logged. See {@link #safeJdbcUrl(String)}.
 */
@Component
public class StartupInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private final Environment environment;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public StartupInfoLogger(Environment environment, DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupSummary() {
        String[] profiles = environment.getActiveProfiles();
        log.info("Application started: name={}, profiles={}, address={}, port={}",
                environment.getProperty("spring.application.name"),
                profiles.length == 0 ? "[default]" : Arrays.toString(profiles),
                environment.getProperty("server.address"),
                environment.getProperty("local.server.port", environment.getProperty("server.port")));
        log.info("Database configuration: url={}, user={}, ddl-auto={}",
                safeJdbcUrl(environment.getProperty("spring.datasource.url")),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        logDatabaseStatus();
    }

    private void logDatabaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            String schemaVersion = jdbcTemplate.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
            log.info("Database connection OK: product={} {}, database={}, flywaySchemaVersion={}",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion(),
                    connection.getCatalog(),
                    schemaVersion);
        } catch (SQLException | RuntimeException e) {
            // Only the exception type is logged: driver messages can contain connection details.
            log.error("Database check failed ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Returns the JDBC URL without credentials: drops {@code user:password@} and the whole query string
     * (which may contain {@code password=...}). Example:
     * {@code jdbc:postgresql://localhost:5432/ebookstore_db?password=x} becomes
     * {@code jdbc:postgresql://localhost:5432/ebookstore_db}.
     */
    static String safeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(not set)";
        }
        String withoutQuery = url.split("[?;]", 2)[0];
        return withoutQuery.replaceFirst("//[^/@]*@", "//");
    }
}
