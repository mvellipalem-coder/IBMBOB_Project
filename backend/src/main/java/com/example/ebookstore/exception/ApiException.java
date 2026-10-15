package com.example.ebookstore.exception;

/**
 * The one exception that services throw when a business rule or lookup fails.
 *
 * <p>Example: {@code throw new ApiException(ErrorCode.BOOK_NOT_FOUND);}
 * {@link GlobalExceptionHandler} turns it into the JSON error body from the OpenAPI contract, so
 * controllers and services never build error responses by hand.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Uses the default message of the error code. */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * Uses a more specific message, for example {@code Only 2 copies of "Clean Code" are available.}
     * The message is sent to the client, so it must never contain secrets or other users' data.
     */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
