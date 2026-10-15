package com.example.ebookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/login} (schema {@code LoginRequest} in openapi.yaml). Unknown fields are rejected. */
public record LoginRequest(

        @NotBlank @Size(max = 254) @Email
        String email,

        @NotBlank @MaxUtf8Bytes(72)
        String password) {

    /** The password and e-mail must never appear in a log line, so nothing is printed. */
    @Override
    public String toString() {
        return "LoginRequest[****]";
    }
}
