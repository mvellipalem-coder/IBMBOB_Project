package com.example.ebookstore.dto;

/** A line from the old order that was not (fully) added to the cart (schema {@code BuyAgainIssue}). */
public record BuyAgainIssue(
        Long bookId,
        String title,
        String reason,
        int requestedQuantity,
        int addedQuantity) {
}
