package com.example.ebookstore.service;

import com.example.ebookstore.dto.PaymentProcessResult;
import com.example.ebookstore.dto.PaymentResponse;

/**
 * Payment processing and retrieval service contract.
 */
public interface PaymentService {

    /**
     * Processes payment for an existing order with idempotency protection.
     *
     * @param userId authenticated user ID
     * @param orderId target order ID
     * @param idempotencyKey client idempotency key
     * @return payment result with response and replayed indicator
     */
    PaymentProcessResult processPayment(Long userId, Long orderId, String idempotencyKey);

    /**
     * Retrieves payment details for an existing order.
     *
     * @param userId authenticated user ID
     * @param orderId target order ID
     * @return payment details
     */
    PaymentResponse getPayment(Long userId, Long orderId);
}
