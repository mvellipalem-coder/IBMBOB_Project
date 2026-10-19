package com.example.ebookstore.dto;

import java.util.List;

/** A page of order summaries (schema {@code OrderPage}). */
public record OrderPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<OrderSummary> content) {
}
