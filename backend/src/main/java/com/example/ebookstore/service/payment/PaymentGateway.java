package com.example.ebookstore.service.payment;

/**
 * Interface behind which simulated or real payment gateways (Stripe, Razorpay, etc.) operate.
 */
public interface PaymentGateway {

    /**
     * Attempts to authorize and settle payment for the given request.
     *
     * @param request order and payment details
     * @return provider response with success/failure and gateway transaction reference
     */
    PaymentGatewayResponse processPayment(PaymentGatewayRequest request);

    /**
     * Attempts to refund a settled transaction through the provider.
     *
     * @param request refund request details
     * @return provider response with refund status
     */
    PaymentGatewayResponse refundPayment(PaymentGatewayRefundRequest request);
}
