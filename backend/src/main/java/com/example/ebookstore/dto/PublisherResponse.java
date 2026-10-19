package com.example.ebookstore.dto;

/** A book publisher (schema {@code Publisher} in openapi.yaml). */
public record PublisherResponse(long id, String name, String description) {
}
