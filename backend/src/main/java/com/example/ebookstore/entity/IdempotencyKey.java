package com.example.ebookstore.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A remembered {@code Idempotency-Key} (table {@code idempotency_keys}). Keys are scoped to user + operation.
 * It stores a hash of the request (to detect "same key, different request") and the order that was created.
 * The key text is client-chosen but only holds letters, digits, '-' and '_' (checked by the API and the database).
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 40, updatable = false)
    private String operation;

    /** Named {@code keyValue} because {@code KEY} is a reserved word in JPQL. */
    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String keyValue;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Required by JPA. */
    protected IdempotencyKey() {
    }

    public IdempotencyKey(Long userId, String operation, String keyValue, String requestHash, Long orderId,
                          Instant expiresAt) {
        this.userId = userId;
        this.operation = operation;
        this.keyValue = keyValue;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.expiresAt = expiresAt;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
