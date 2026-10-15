package com.example.ebookstore.dto;

import java.time.Instant;

import com.example.ebookstore.entity.Role;

/** Public view of a user (schema {@code UserProfile}). It has no password and no password hash. */
public record UserProfile(
        long id,
        String fullName,
        String email,
        String phone,
        Role role,
        Instant createdAt) {

    /** Personal data (name, e-mail, phone) is left out so framework debug logging cannot leak it. */
    @Override
    public String toString() {
        return "UserProfile[id=" + id + ", role=" + role + "]";
    }
}
