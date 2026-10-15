package com.example.ebookstore.dto;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.example.ebookstore.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The standard body of every error response (schema {@code ErrorResponse} in openapi.yaml).
 * Clients switch on {@code code}; {@code message} is for humans.
 * It never contains stack traces, SQL or class names.
 *
 * @param timestamp   when the error happened (ISO 8601, UTC)
 * @param status      HTTP status code, e.g. 409
 * @param error       HTTP reason phrase, e.g. "Conflict"
 * @param code        stable machine-readable code, e.g. INSUFFICIENT_STOCK
 * @param message     human-readable explanation
 * @param path        request path
 * @param fieldErrors validation problems; only present for VALIDATION_FAILED
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<ApiFieldError> fieldErrors) {

    /** Builds an error body for the given code, message and request path. */
    public static ErrorResponse of(ErrorCode code, String message, String path, List<ApiFieldError> fieldErrors) {
        HttpStatus status = code.getStatus();
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code.name(),
                message, path, fieldErrors);
    }

    /** Same as above, without field errors. */
    public static ErrorResponse of(ErrorCode code, String message, String path) {
        return of(code, message, path, null);
    }
}
