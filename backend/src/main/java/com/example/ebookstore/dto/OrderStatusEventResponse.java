package com.example.ebookstore.dto;

import java.time.Instant;

import com.example.ebookstore.domain.OrderStatus;

/** One step of the order timeline (schema {@code OrderStatusEvent}). */
public record OrderStatusEventResponse(OrderStatus status, String note, Instant changedAt) {
}
