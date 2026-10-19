package com.example.ebookstore.entity;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.example.ebookstore.domain.GiftPointTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry of the gift-point ledger (table {@code gift_point_transactions}). The ledger is append-only: the
 * database rejects updates and deletes, and this entity is {@link Immutable} and read-only in Java. New rows are
 * written only through {@code GiftPointTransactionRepository#insertRedeem}, because the database trigger fills
 * {@code balance_after} and updates the user's balance in the same statement.
 */
@Entity
@Immutable
@Table(name = "gift_point_transactions")
public class GiftPointTransaction {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GiftPointTransactionType type;

    @Column(nullable = false)
    private int points;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected GiftPointTransaction() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public GiftPointTransactionType getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
