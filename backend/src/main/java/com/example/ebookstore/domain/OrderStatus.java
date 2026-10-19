package com.example.ebookstore.domain;

/** Order lifecycle (schema {@code OrderStatus}): PLACED -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED, or CANCELLED. */
public enum OrderStatus {
    PLACED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED;

    /** Only these states can still be cancelled (and only inside the cancellation window). */
    public boolean isCancellableState() {
        return this == PLACED || this == CONFIRMED || this == PACKED;
    }
}
