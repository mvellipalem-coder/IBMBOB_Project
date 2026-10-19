package com.example.ebookstore.dto;

import java.util.List;

/** Result of {@code POST /api/orders/{id}/buy-again} (schema {@code BuyAgainResult}). */
public record BuyAgainResult(
        CartResponse cart,
        List<BuyAgainIssue> issues) {
}
