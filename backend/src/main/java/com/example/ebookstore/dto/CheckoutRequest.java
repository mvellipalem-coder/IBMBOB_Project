package com.example.ebookstore.dto;

import com.example.ebookstore.domain.PaymentMethod;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/checkout} (schema {@code CheckoutRequest}).
 *
 * <p>There are deliberately no prices, totals, discounts, statuses or user id: unknown JSON fields are rejected
 * with {@code 400}, so a client cannot influence anything except the address, the payment method and how many
 * gift points to spend. The server checks the points against the balance and the redemption cap.
 */
public record CheckoutRequest(

        @NotNull @Min(1)
        Long addressId,

        @NotNull
        PaymentMethod paymentMethod,

        @Min(0) @Max(1_000_000)
        Integer pointsToRedeem) {

    /** Points to spend: the requested number, or 0 when the client did not send it. */
    public int pointsOrZero() {
        return pointsToRedeem == null ? 0 : pointsToRedeem;
    }
}
