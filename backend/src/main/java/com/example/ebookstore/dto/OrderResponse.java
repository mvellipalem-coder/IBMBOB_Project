package com.example.ebookstore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.example.ebookstore.domain.OrderStatus;

/**
 * Full order view (schema {@code Order}). Every value is computed or stored by the server.
 *
 * @param totalAmount always {@code subtotal - pointsDiscount}; the amount to pay
 * @param pointsEarned points credited once the order is delivered (never at checkout)
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        LocalDate expectedDeliveryDate,
        Instant deliveredAt,
        Instant cancelledAt,
        String cancelReason,
        ShippingAddressResponse shippingAddress,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        int pointsRedeemed,
        BigDecimal pointsDiscount,
        BigDecimal totalAmount,
        int pointsEarned,
        PaymentResponse payment,
        List<OrderStatusEventResponse> statusHistory,
        CancellationInfo cancellation) {
}
