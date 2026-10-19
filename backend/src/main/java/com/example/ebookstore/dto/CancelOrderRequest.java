package com.example.ebookstore.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/orders/{id}/cancel} (schema {@code CancelOrderRequest}).
 * The entire body is optional; {@code reason} defaults to "Cancelled by customer".
 */
public record CancelOrderRequest(
        @Size(max = 255, message = "reason cannot exceed 255 characters")
        String reason) {

    public String reasonOrDefault() {
        return reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by customer";
    }
}
