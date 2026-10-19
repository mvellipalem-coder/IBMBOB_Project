package com.example.ebookstore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.RecommendationList;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.RecommendationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Endpoint for personalized and popular book recommendations.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<RecommendationList> getRecommendations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "limit", defaultValue = "10")
            @Min(value = 1, message = "must be at least 1")
            @Max(value = 20, message = "must be at most 20")
            int limit) {
        RecommendationList recommendations = recommendationService.getRecommendations(user.id(), limit);
        return ResponseEntity.ok(recommendations);
    }
}
