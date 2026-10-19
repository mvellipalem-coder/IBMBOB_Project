package com.example.ebookstore.dto;

/**
 * Outcome of adding a book: the updated cart, and whether a new line was created ({@code 201}) or an existing
 * line was increased ({@code 200}).
 */
public record AddToCartResult(CartResponse cart, boolean created) {
}
