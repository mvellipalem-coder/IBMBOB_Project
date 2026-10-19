package com.example.ebookstore.dto;

import java.math.BigDecimal;

/**
 * Everything the catalogue needs to answer "list" and "search" requests. Every filter is optional ({@code null}
 * means "do not filter on this").
 *
 * @param query       search text; {@code null} for the plain list endpoint
 * @param categoryId  only books in this category
 * @param publisherId only books from this publisher
 * @param minPrice    lowest price, inclusive
 * @param maxPrice    highest price, inclusive
 * @param inStock     {@code true} = only books with stock; {@code null}/{@code false} = all books
 * @param minRating   lowest average rating, inclusive
 * @param sort        sort order, never {@code null}
 * @param page        zero-based page number
 * @param size        page size (1 to 100)
 */
public record BookSearchCriteria(
        String query,
        Long categoryId,
        Long publisherId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        BigDecimal minRating,
        BookSort sort,
        int page,
        int size) {
}
