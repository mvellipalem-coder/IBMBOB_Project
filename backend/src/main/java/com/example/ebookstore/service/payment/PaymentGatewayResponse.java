package com.example.ebookstore.service.payment;

/**
 * Result returned by a payment gateway provider.
 */
public record PaymentGatewayResponse(
        boolean success,
        String transactionRef,
        String failureReason) {

    public static PaymentGatewayResponse success(String transactionRef) {
        return new PaymentGatewayResponse(true, transactionRef, null);
    }

    public static PaymentGatewayResponse failure(String failureReason) {
        return new PaymentGatewayResponse(false, null, failureReason);
    }
}
