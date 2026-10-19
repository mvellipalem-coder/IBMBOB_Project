package com.example.ebookstore.entity;

import java.time.Instant;

import com.example.ebookstore.domain.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry of an order's tracking timeline (table {@code order_status_history}). Append-only by convention. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrderStatus status;

    @Column(length = 255, updatable = false)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /** Required by JPA. */
    protected OrderStatusHistory() {
    }

    public OrderStatusHistory(OrderStatus status, String note, Instant changedAt) {
        this.status = status;
        this.note = note;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
