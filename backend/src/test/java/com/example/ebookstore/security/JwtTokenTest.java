package com.example.ebookstore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.JwtConfig;
import com.example.ebookstore.config.JwtProperties;
import com.example.ebookstore.entity.Role;
import com.example.ebookstore.entity.User;
import com.example.ebookstore.repository.UserRepository;

/** Tests token creation, verification and the conversion to the logged-in user, without a web server. */
class JwtTokenTest {

    private static final String SECRET = "unit-test-secret-with-more-than-32-characters!";

    private final JwtProperties properties = new JwtProperties(SECRET, 1800, "ebookstore");
    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSigningKey(properties);
    private final JwtTokenService tokenService = new JwtTokenService(config.jwtEncoder(key), properties);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);

    @Test
    void tokenContainsOnlyIdRoleIssuerAndTimes() {
        Jwt jwt = decoder.decode(tokenService.createToken(user(7L, Role.CUSTOMER, true)));

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ebookstore");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(1700)).isBefore(Instant.now().plusSeconds(1900));
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "iss", "iat", "exp", "jti");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties other = new JwtProperties("a-completely-different-secret-of-32-chars!!", 1800, "ebookstore");
        JwtTokenService forger = new JwtTokenService(config.jwtEncoder(config.jwtSigningKey(other)), other);

        assertThatThrownBy(() -> decoder.decode(forger.createToken(user(7L, Role.ADMIN, true))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant longAgo = Instant.now().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ebookstore").subject("7")
                .issuedAt(longAgo).expiresAt(longAgo.plusSeconds(1800)).build();
        String expired = config.jwtEncoder(key).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithAnotherIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, 1800, "someone-else");
        JwtTokenService foreign = new JwtTokenService(config.jwtEncoder(key), otherIssuer);

        assertThatThrownBy(() -> decoder.decode(foreign.createToken(user(7L, Role.CUSTOMER, true))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenService.createToken(user(7L, Role.CUSTOMER, true));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void converterBuildsThePrincipalFromTheDatabaseNotFromTheTokenRole() {
        UserRepository repository = mock(UserRepository.class);
        // The token claims ADMIN, but the database says CUSTOMER.
        when(repository.findById(7L)).thenReturn(Optional.of(user(7L, Role.CUSTOMER, true)));
        Jwt claimsAdmin = Jwt.withTokenValue("t").header("alg", "HS256").subject("7").claim("role", "ADMIN").build();

        AbstractAuthenticationToken auth = new JwtAuthenticationConverter(repository).convert(claimsAdmin);

        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedUser(7L, Role.CUSTOMER));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void converterRejectsUnknownDisabledAndMalformedSubjects() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.findById(2L)).thenReturn(Optional.of(user(2L, Role.CUSTOMER, false)));
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter(repository);

        for (String subject : new String[] {"1", "2", "abc"}) {
            Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(subject).build();
            assertThatThrownBy(() -> converter.convert(jwt)).as("subject '%s'", subject)
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    private static User user(long id, Role role, boolean enabled) {
        User user = new User("Asha Rao", "asha@example.com",
                "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234", null, role);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }
}
