package com.example.ebookstore.exception;

import org.springframework.http.HttpStatus;

/**
 * Every machine-readable error code of the API.
 *
 * <p>These names and HTTP statuses are copied from the approved {@code openapi.yaml}
 * ({@code ErrorResponse.code}). A unit test ({@code ErrorCodeContractTest}) fails if this enum and the
 * contract ever differ, so the running API cannot drift away from the documentation.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid e-mail or password."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "This e-mail address is already registered."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later."),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "Book not found."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Address not found."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Not enough copies are available."),
    CART_LINE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "You can order at most 10 copies of one book."),
    ADDRESS_LIMIT_REACHED(HttpStatus.CONFLICT, "You can save at most 10 addresses."),
    EMPTY_CART(HttpStatus.CONFLICT, "Your cart is empty."),
    INVALID_POINTS_REDEMPTION(HttpStatus.UNPROCESSABLE_CONTENT, "The requested gift points cannot be redeemed."),
    IDEMPOTENCY_KEY_IN_PROGRESS(HttpStatus.CONFLICT,
            "A request with this Idempotency-Key is still being processed. Retry shortly."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This Idempotency-Key was already used for a different request."),
    PAYMENT_ALREADY_SETTLED(HttpStatus.CONFLICT, "This payment has already been processed."),
    ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "This order cannot be paid."),
    PAYMENT_NOT_REQUIRED(HttpStatus.CONFLICT, "Cash on delivery orders are paid on delivery."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "This order is already cancelled."),
    ORDER_NOT_CANCELLABLE_STATUS(HttpStatus.CONFLICT, "Orders that are SHIPPED or DELIVERED cannot be cancelled."),
    CANCELLATION_WINDOW_EXPIRED(HttpStatus.CONFLICT,
            "Orders can only be cancelled within 48 hours of being placed."),
    NO_ITEMS_AVAILABLE(HttpStatus.CONFLICT, "None of the books in this order are currently available."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** HTTP status that goes with this error. */
    public HttpStatus getStatus() {
        return status;
    }

    /** Safe, generic message used when the code is thrown without a more specific one. */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
