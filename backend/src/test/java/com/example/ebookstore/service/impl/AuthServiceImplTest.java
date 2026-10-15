package com.example.ebookstore.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.dto.AuthResponse;
import com.example.ebookstore.dto.LoginRequest;
import com.example.ebookstore.dto.RegisterRequest;
import com.example.ebookstore.dto.UserProfile;
import com.example.ebookstore.entity.Role;
import com.example.ebookstore.entity.User;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.UserMapper;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.security.JwtTokenService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String PASSWORD = "Correct-Horse-9";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenService, new UserMapper());
    }

    // ------------------------------------------------------------------ register

    @Test
    void registerStoresABcryptHashLowerCaseEmailAndAlwaysTheCustomerRole() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        UserProfile profile = service.register(
                new RegisterRequest("  Asha Rao ", "  Asha@Example.COM ", PASSWORD, " "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getFullName()).isEqualTo("Asha Rao");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(profile.id()).isEqualTo(7L);
        assertThat(profile.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha Rao", "ASHA@example.com", PASSWORD, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerReportsARaceOnTheUniqueIndexAsEmailAlreadyRegistered() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha Rao", "asha@example.com", PASSWORD, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginReturnsATokenForCorrectCredentialsIgnoringEmailCase() {
        User user = existingUser(true);
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.createToken(user)).thenReturn("signed.jwt.value");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(1800L);

        AuthResponse response = service.login(new LoginRequest("Asha@Example.com", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        assertThat(response.user().email()).isEqualTo("asha@example.com");
    }

    @Test
    void loginGivesTheSameGenericErrorForWrongPasswordUnknownEmailAndDisabledAccount() {
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(existingUser(true)));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("off@example.com")).thenReturn(Optional.of(existingUser(false)));

        LoginRequest[] badRequests = {
                new LoginRequest("asha@example.com", "Wrong-Password-1"),
                new LoginRequest("nobody@example.com", PASSWORD),
                new LoginRequest("off@example.com", PASSWORD)};
        for (LoginRequest bad : badRequests) {
            assertThatThrownBy(() -> service.login(bad))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                        assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
                    });
        }
        verify(jwtTokenService, never()).createToken(any());
    }

    @Test
    void toStringOfRequestsAndResponsesNeverShowsThePasswordOrToken() {
        assertThat(new LoginRequest("a@example.com", PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new RegisterRequest("A B", "a@example.com", PASSWORD, null).toString()).doesNotContain(PASSWORD);
        assertThat(new AuthResponse("secret.token.value", "Bearer", 1800, null).toString())
                .doesNotContain("secret.token.value");
    }

    private User existingUser(boolean enabled) {
        User user = new User("Asha Rao", "asha@example.com", passwordEncoder.encode(PASSWORD), null, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 7L);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }
}
