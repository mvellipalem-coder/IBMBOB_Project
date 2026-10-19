package com.example.ebookstore.service;

import com.example.ebookstore.dto.CheckoutRequest;
import com.example.ebookstore.dto.CheckoutResult;

/** Turns the cart into an order. */
public interface CheckoutService {

    /**
     * Creates an order from the user's cart in ONE database transaction (items, address copy, pending payment,
     * stock decrement, gift-point redemption, cart clearing), or changes nothing at all.
     *
     * @param idempotencyKey already format-checked by the controller; a retry with the same key and the same request
     *                       returns the existing order instead of creating a second one
     * @throws com.example.ebookstore.exception.ApiException {@code EMPTY_CART}, {@code ADDRESS_NOT_FOUND},
     *         {@code INSUFFICIENT_STOCK}, {@code INVALID_POINTS_REDEMPTION}, {@code IDEMPOTENCY_KEY_REUSED} or
     *         {@code IDEMPOTENCY_KEY_IN_PROGRESS}
     */
    CheckoutResult checkout(long userId, String idempotencyKey, CheckoutRequest request);
}
