package com.example.ebookstore.service;

import com.example.ebookstore.dto.AuthResponse;
import com.example.ebookstore.dto.LoginRequest;
import com.example.ebookstore.dto.RegisterRequest;
import com.example.ebookstore.dto.UserProfile;

/** Registration and login. */
public interface AuthService {

    /**
     * Creates a CUSTOMER account.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code EMAIL_ALREADY_REGISTERED} if the e-mail is taken
     */
    UserProfile register(RegisterRequest request);

    /**
     * Checks the credentials and returns a JWT.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code INVALID_CREDENTIALS} for a wrong e-mail,
     *         a wrong password or a disabled account (deliberately the same answer for all three)
     */
    AuthResponse login(LoginRequest request);
}
