package com.example.ebookstore.dto;

/** Result of processing a payment: the payment and whether it was replayed from idempotency. */
public record PaymentProcessResult(
        PaymentResponse payment,
        boolean replayed) {
}
