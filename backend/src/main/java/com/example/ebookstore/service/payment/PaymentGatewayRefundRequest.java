package com.example.ebookstore.service.payment;

import java.math.BigDecimal;

/**
 * Refund request submitted to the payment gateway provider.
 */
public record PaymentGatewayRefundRequest(
        Long orderId,
        String transactionRef,
        BigDecimal amount) {
}
