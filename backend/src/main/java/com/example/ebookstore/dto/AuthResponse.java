package com.example.ebookstore.dto;

/**
 * Response of a successful login (schema {@code AuthResponse}).
 * The client sends the token back as {@code Authorization: Bearer <accessToken>}.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfile user) {

    public static final String BEARER = "Bearer";

    /** The token must never appear in a log line, so it is masked here. */
    @Override
    public String toString() {
        return "AuthResponse[accessToken=****, tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", user=" + user + "]";
    }
}
