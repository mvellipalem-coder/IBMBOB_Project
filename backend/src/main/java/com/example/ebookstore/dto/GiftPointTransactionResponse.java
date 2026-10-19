package com.example.ebookstore.dto;

import java.time.Instant;

import com.example.ebookstore.domain.GiftPointTransactionType;

/** One immutable ledger entry (schema {@code GiftPointTransaction}). {@code points} is signed and never 0. */
public record GiftPointTransactionResponse(
        Long id,
        GiftPointTransactionType type,
        int points,
        int balanceAfter,
        Long orderId,
        String description,
        Instant createdAt) {
}
