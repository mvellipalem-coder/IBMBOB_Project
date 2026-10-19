package com.example.ebookstore.domain;

/**
 * Kinds of gift-point ledger entries (schema {@code GiftPointTransactionType}).
 * EARN and REFUND are positive, REDEEM is negative, ADJUST is a manual correction (either sign).
 */
public enum GiftPointTransactionType {
    EARN, REDEEM, REFUND, ADJUST
}
