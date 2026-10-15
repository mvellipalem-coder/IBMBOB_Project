package com.example.ebookstore.dto;

/**
 * One validation problem inside an {@link ErrorResponse} (schema {@code FieldError} in openapi.yaml).
 * The rejected value is deliberately not included: it could be a password.
 *
 * @param field   name of the request field, query parameter or header
 * @param message what is wrong, for example "must be between 1 and 10"
 */
public record ApiFieldError(String field, String message) {
}
