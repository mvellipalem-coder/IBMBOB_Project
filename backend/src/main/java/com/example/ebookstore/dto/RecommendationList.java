package com.example.ebookstore.dto;

import java.util.List;

import com.example.ebookstore.domain.RecommendationStrategy;

/**
 * Recommended books and the strategy that produced them (schema {@code RecommendationList}).
 */
public record RecommendationList(
        RecommendationStrategy strategy,
        List<BookSummary> books) {
}
