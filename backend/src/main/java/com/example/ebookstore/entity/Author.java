package com.example.ebookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A book author (table {@code authors}). A book can have several authors. Read-only for the public API. */
@Entity
@Table(name = "authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Required by JPA. */
    protected Author() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
