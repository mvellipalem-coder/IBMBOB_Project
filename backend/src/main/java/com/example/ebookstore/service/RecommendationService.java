package com.example.ebookstore.service;

import com.example.ebookstore.dto.RecommendationList;

/**
 * Service for book recommendations.
 */
public interface RecommendationService {

    /**
     * Recommends books for the given user up to {@code limit}.
     *
     * @param userId authenticated user ID
     * @param limit maximum number of recommendations
     * @return recommended books and strategy
     */
    RecommendationList getRecommendations(Long userId, int limit);
}
