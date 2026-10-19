package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A book as shown in lists, search results and related-book rows (schema {@code BookSummary} in openapi.yaml).
 *
 * @param inStock true when at least one copy is available (the exact stock level is not exposed by the contract)
 */
public record BookSummary(
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
        Integer publishedYear) {
}
