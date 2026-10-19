package com.example.ebookstore.service.payment;

import java.math.BigDecimal;

import com.example.ebookstore.domain.PaymentMethod;

/**
 * Details sent to the payment gateway to process a charge.
 * Note: Never contains raw credit card, CVV, PIN or customer secret credentials.
 */
public record PaymentGatewayRequest(
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        PaymentMethod method) {
}
