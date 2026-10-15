package com.example.ebookstore.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.example.ebookstore.config.JwtProperties;
import com.example.ebookstore.entity.User;

/**
 * Creates the signed JWT access token returned by login.
 *
 * <p>The token carries only: {@code sub} (user id), {@code role}, {@code iss}, {@code iat}, {@code exp}, {@code jti}.
 * It contains no password, no e-mail address and no other personal data. The token itself is never logged.
 */
@Component
public class JwtTokenService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /** Returns a new token for the user. */
    public String createToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.expirationSeconds()))
                .id(UUID.randomUUID().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** How long a token stays valid, in seconds (returned to the client as {@code expiresIn}). */
    public long getExpirationSeconds() {
        return properties.expirationSeconds();
    }
}
