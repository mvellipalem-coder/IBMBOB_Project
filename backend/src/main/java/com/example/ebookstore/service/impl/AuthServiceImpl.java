package com.example.ebookstore.service.impl;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.ebookstore.service.AuthService;

/**
 * Registration and login rules.
 *
 * <p>Logging policy: passwords, password hashes, tokens and e-mail addresses are never written to the log.
 * Only the numeric user id is logged.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    /**
     * A BCrypt hash of a throw-away value. When the e-mail is unknown we still compare against it, so
     * "unknown e-mail" and "wrong password" take about the same time and attackers cannot tell them apart.
     */
    private final String timingProtectionHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.timingProtectionHash = passwordEncoder.encode("not-a-real-password");
    }

    @Override
    @Transactional
    public UserProfile register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // The role is fixed here on the server. Nothing in the request can change it.
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()),
                blankToNull(request.phone()), Role.CUSTOMER);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two people registered the same e-mail at the same moment; the database unique index caught it.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        log.info("User registered: userId={}", user.getId());
        return userMapper.toProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normaliseEmail(request.email())).orElse(null);

        String hashToCheck = user != null ? user.getPasswordHash() : timingProtectionHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches || !user.isEnabled()) {
            // No e-mail in the log: it is personal data and could be a typo of a real password.
            log.warn("Login failed");
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("Login succeeded: userId={}", user.getId());
        return new AuthResponse(jwtTokenService.createToken(user), AuthResponse.BEARER,
                jwtTokenService.getExpirationSeconds(), userMapper.toProfile(user));
    }

    /** E-mail addresses are stored and compared in lower case, without surrounding spaces. */
    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
