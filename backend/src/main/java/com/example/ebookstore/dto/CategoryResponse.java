package com.example.ebookstore.dto;

/** A book category (schema {@code Category} in openapi.yaml). */
public record CategoryResponse(long id, String name, String description) {
}
