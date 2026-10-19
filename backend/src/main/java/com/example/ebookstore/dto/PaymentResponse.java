package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;

/** The payment of an order (schema {@code Payment}). No card, CVV or UPI data exists in this API. */
public record PaymentResponse(
        Long orderId,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String transactionRef,
        String failureReason,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt) {
}
