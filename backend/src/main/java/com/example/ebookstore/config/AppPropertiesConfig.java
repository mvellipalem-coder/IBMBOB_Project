package com.example.ebookstore.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on automatic loading of every {@code @ConfigurationProperties} class in this package
 * (for example {@link JwtProperties} and {@link BusinessProperties}).
 *
 * <p>Kept in its own small class so that test slices (such as {@code @WebMvcTest}) do not need
 * secrets like {@code JWT_SECRET} unless they ask for them.
 */
@Configuration
@ConfigurationPropertiesScan(basePackageClasses = AppPropertiesConfig.class)
public class AppPropertiesConfig {
}
