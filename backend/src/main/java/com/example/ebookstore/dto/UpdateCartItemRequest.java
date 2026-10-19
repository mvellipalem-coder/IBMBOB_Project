package com.example.ebookstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/cart/items/{itemId}} (schema {@code UpdateCartItemRequest}). Sets the quantity; zero and
 * negative values are rejected (use {@code DELETE} to remove a line).
 */
public record UpdateCartItemRequest(

        @NotNull(message = "is required")
        @Min(value = 1, message = "must be between 1 and 10")
        @Max(value = 10, message = "must be between 1 and 10")
        Integer quantity) {
}
