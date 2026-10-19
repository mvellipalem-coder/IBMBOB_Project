package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart (schema {@code Cart} in openapi.yaml). Everything here is computed by the server from current
 * catalogue prices; nothing is taken from the client.
 *
 * @param totalQuantity total number of copies over all lines
 * @param subtotal      sum of all line totals
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal) {
}
