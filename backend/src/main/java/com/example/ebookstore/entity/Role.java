package com.example.ebookstore.entity;

/**
 * What a user may do. Stored as text in {@code users.role} (the database only allows these two values).
 * Self-registration always creates a {@link #CUSTOMER}; an {@link #ADMIN} can only be created directly in the database.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
