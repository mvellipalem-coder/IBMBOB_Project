package com.example.ebookstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/cart/items} (schema {@code AddCartItemRequest} in openapi.yaml).
 *
 * <p>There is deliberately no price, total or user id: unknown JSON fields are rejected with {@code 400}, so the
 * client cannot suggest what a book costs. {@code quantity} defaults to 1 when omitted.
 */
public record AddCartItemRequest(

        @NotNull @Min(1)
        Long bookId,

        @Min(value = 1, message = "must be between 1 and 10")
        @Max(value = 10, message = "must be between 1 and 10")
        Integer quantity) {

    /** The quantity to add: the requested one, or 1 when the client did not send it. */
    public int quantityOrDefault() {
        return quantity == null ? 1 : quantity;
    }
}
