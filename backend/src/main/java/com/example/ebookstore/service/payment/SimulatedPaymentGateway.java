package com.example.ebookstore.service.payment;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Simulated payment gateway provider for local demo and test environments.
 *
 * <p>Supports simulated credit card, debit card, and mock payment (UPI) transactions without collecting
 * or storing real card numbers, CVVs, or sensitive credentials. Outcomes are simulated safely on the server.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final String DEFAULT_FAILURE_REASON = "Payment declined by the simulated gateway";
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);
    private final AtomicReference<String> failureReason = new AtomicReference<>(DEFAULT_FAILURE_REASON);

    @Override
    public PaymentGatewayResponse processPayment(PaymentGatewayRequest request) {
        if (simulateFailure.get()) {
            String reason = failureReason.get();
            return PaymentGatewayResponse.failure(reason != null ? reason : DEFAULT_FAILURE_REASON);
        }

        String transactionRef = generateTransactionRef();
        return PaymentGatewayResponse.success(transactionRef);
    }

    @Override
    public PaymentGatewayResponse refundPayment(PaymentGatewayRefundRequest request) {
        if (simulateFailure.get()) {
            String reason = failureReason.get();
            return PaymentGatewayResponse.failure(reason != null ? reason : DEFAULT_FAILURE_REASON);
        }

        String transactionRef = request.transactionRef() != null ? request.transactionRef() : generateTransactionRef();
        return PaymentGatewayResponse.success(transactionRef);
    }

    /**
     * Enables or disables simulated failure for testing and demo flows.
     */
    public void setSimulateFailure(boolean fail) {
        this.simulateFailure.set(fail);
    }

    /**
     * Sets a custom failure reason for simulated failure scenarios.
     */
    public void setFailureReason(String reason) {
        this.failureReason.set(reason);
    }

    /**
     * Resets simulation configuration to default successful processing.
     */
    public void reset() {
        this.simulateFailure.set(false);
        this.failureReason.set(DEFAULT_FAILURE_REASON);
    }

    private String generateTransactionRef() {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return "SIM-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
