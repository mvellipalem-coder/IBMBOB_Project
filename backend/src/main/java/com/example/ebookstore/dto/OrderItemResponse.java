package com.example.ebookstore.dto;

import java.math.BigDecimal;

/** What was bought, frozen at checkout (schema {@code OrderItem}). */
public record OrderItemResponse(
        Long bookId,
        String title,
        String isbn,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {
}
