package com.example.ebookstore.domain;

/**
 * Payment methods a customer can choose (schema {@code PaymentMethod}). Gift points are deliberately not a method:
 * they are a partial discount that can be combined with any of these.
 */
public enum PaymentMethod {
    CARD, UPI, COD
}
