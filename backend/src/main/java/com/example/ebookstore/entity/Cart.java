package com.example.ebookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The shopping cart of one user (table {@code carts}; the database allows exactly one row per user).
 *
 * <p>Rows are created by {@code CartRepository#insertIfAbsent} (an {@code INSERT ... ON CONFLICT DO NOTHING}),
 * so two simultaneous first requests of the same user cannot create two carts. Timestamps are maintained by the
 * database and are not mapped. This class is never returned from a controller.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Required by JPA. */
    protected Cart() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }
}
