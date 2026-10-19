package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A book on its detail page (schema {@code BookDetail}): everything in {@link BookSummary} plus the description
 * and the most copies a customer can put in one cart line.
 *
 * @param maxOrderableQuantity smaller of the available stock and the per-line limit (10); 0 means out of stock
 */
public record BookDetail(
        long id,
        String title,
        List<String> authors,
        String isbn,
        BigDecimal price,
        String imageUrl,
        BigDecimal ratingAvg,
        int ratingCount,
        boolean inStock,
        long categoryId,
        String categoryName,
        long publisherId,
        String publisherName,
        Integer publishedYear,
        String description,
        int maxOrderableQuantity) {
}
