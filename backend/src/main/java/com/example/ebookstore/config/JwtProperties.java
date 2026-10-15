package com.example.ebookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * JWT settings, read from {@code app.security.jwt.*} in application.properties.
 * The secret itself is never written in a file: it comes from the {@code JWT_SECRET} environment variable.
 *
 * @param secret            key used to sign and verify tokens (at least 32 characters)
 * @param expirationSeconds how long a token stays valid
 * @param issuer            name written into every token ("iss" claim)
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters long") String secret,
        @Positive long expirationSeconds,
        @NotBlank String issuer) {

    /** Hides the secret so it can never end up in a log line by accident. */
    @Override
    public String toString() {
        return "JwtProperties[secret=****, expirationSeconds=" + expirationSeconds + ", issuer=" + issuer + "]";
    }
}
