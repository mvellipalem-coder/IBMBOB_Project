package com.example.ebookstore.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.ebookstore.security.JwtAuthenticationConverter;
import com.example.ebookstore.security.RestAccessDeniedHandler;
import com.example.ebookstore.security.RestAuthenticationEntryPoint;

/**
 * Who may call what. The public/protected split matches {@code security: []} in openapi.yaml:
 * registration, login, the whole catalogue and the API documentation are public; everything else needs a JWT.
 *
 * <p>How a request is authenticated:
 * <ol>
 *   <li>The client sends {@code Authorization: Bearer <token>}.</li>
 *   <li>Spring's resource-server filter verifies the signature, expiry and issuer ({@link JwtConfig}).</li>
 *   <li>{@link JwtAuthenticationConverter} loads the user from the database and builds the
 *       {@code AuthenticatedUser} principal.</li>
 *   <li>Missing/invalid token: {@code 401}. Logged in but role not allowed: {@code 403}. Both as JSON.</li>
 * </ol>
 * Security is never switched off; tests log in with real tokens.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            // The API is stateless and uses an Authorization header, not cookies, so CSRF does not apply.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/categories", "/api/publishers").permitAll()
                .requestMatchers("/openapi.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                // Default: deny unless authenticated. New endpoints are protected automatically.
                .anyRequest().authenticated());
        return http.build();
    }

    /** Only the configured front-end origin may call the API from a browser. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("Location", "Idempotent-Replayed", "Retry-After"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
