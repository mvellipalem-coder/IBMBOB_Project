package com.example.ebookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register} (schema {@code RegisterRequest} in openapi.yaml).
 *
 * <p>There is deliberately no {@code role} field, and unknown JSON fields are rejected with {@code 400}.
 * So a client cannot register itself as an administrator.
 */
public record RegisterRequest(

        @NotBlank @Size(min = 2, max = 100)
        String fullName,

        @NotBlank @Size(max = 254) @Email
        @Pattern(regexp = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$", message = "must be a valid e-mail address")
        String email,

        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
                message = "must contain at least one lower-case letter, one upper-case letter and one digit")
        String password,

        @Pattern(regexp = "^[0-9+() -]{7,20}$", message = "may only contain digits, spaces and + - ( )")
        String phone) {

    /** The password and e-mail must never appear in a log line, so nothing is printed. */
    @Override
    public String toString() {
        return "RegisterRequest[****]";
    }
}
