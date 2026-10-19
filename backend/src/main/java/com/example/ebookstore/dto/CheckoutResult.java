package com.example.ebookstore.dto;

/**
 * What the checkout service hands to the controller.
 *
 * @param replayed {@code true} when the order already existed for this {@code Idempotency-Key}
 *                 (the controller then adds the {@code Idempotent-Replayed: true} header)
 */
public record CheckoutResult(OrderResponse order, boolean replayed) {
}
