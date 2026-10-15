package com.example.ebookstore.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.UserProfile;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.UserService;

/**
 * {@code GET /api/users/me} - the profile of whoever owns the token.
 * There is intentionally no "/api/users/{id}" endpoint: nobody can read another user's profile.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getProfile(user.id());
    }
}
