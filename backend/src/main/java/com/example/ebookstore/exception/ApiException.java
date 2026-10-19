package com.example.ebookstore.exception;

import java.util.List;

import com.example.ebookstore.dto.ApiFieldError;

/**
 * The one exception that services throw when a business rule or lookup fails.
 *
 * <p>Example: {@code throw new ApiException(ErrorCode.BOOK_NOT_FOUND);}
 * {@link GlobalExceptionHandler} turns it into the JSON error body from the OpenAPI contract, so
 * controllers and services never build error responses by hand.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiFieldError> fieldErrors;

    /** Uses the default message of the error code. */
    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    /**
     * Uses a more specific message, for example {@code Only 2 copies of "Clean Code" are available.}
     * The message is sent to the client, so it must never contain secrets or other users' data.
     */
    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /** A validation problem that belongs to specific request fields (reported in {@code fieldErrors}). */
    public ApiException(ErrorCode errorCode, String message, List<ApiFieldError> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** May be {@code null}. */
    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}
