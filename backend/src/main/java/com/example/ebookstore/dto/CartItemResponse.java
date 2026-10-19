package com.example.ebookstore.dto;

import java.math.BigDecimal;

/**
 * One cart line (schema {@code CartItem} in openapi.yaml).
 *
 * @param itemId      id of the line, used by {@code PUT} and {@code DELETE /api/cart/items/{itemId}}
 * @param lineTotal   current catalogue price times quantity, computed on the server
 * @param maxQuantity largest quantity currently allowed (smaller of stock and the per-line limit); {@code 0} when
 *                    the book is out of stock or no longer sold. Can be lower than {@code quantity}.
 */
public record CartItemResponse(
        long itemId,
        BookSummary book,
        int quantity,
        BigDecimal lineTotal,
        int maxQuantity) {
}
