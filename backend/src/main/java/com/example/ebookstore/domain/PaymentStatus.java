package com.example.ebookstore.domain;

/** Payment lifecycle (schema {@code PaymentStatus}). Checkout always creates {@code PENDING}. */
public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, REFUNDED
}
