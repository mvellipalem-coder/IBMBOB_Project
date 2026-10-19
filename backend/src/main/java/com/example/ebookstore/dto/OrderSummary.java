package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;

/** Summary view of an order (schema {@code OrderSummary}). */
public record OrderSummary(
        Long id,
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        LocalDate expectedDeliveryDate,
        BigDecimal totalAmount,
        int itemCount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus) {
}
