package com.example.ebookstore.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's clock. Business code asks this clock for "now" (order date, delivery date, key expiry) instead
 * of calling {@code Instant.now()}, so tests can replace it with a fixed one. All dates use UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
