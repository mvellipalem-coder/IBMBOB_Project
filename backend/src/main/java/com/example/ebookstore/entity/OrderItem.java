package com.example.ebookstore.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One purchased line (table {@code order_items}): a snapshot of title, ISBN and price at checkout time.
 * Later catalogue changes never touch it. The database also stores a generated {@code line_total}; it is not
 * mapped here because {@link #getLineTotal()} computes the same value.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false, updatable = false)
    private Long bookId;

    @Column(name = "book_title", nullable = false, updatable = false)
    private String bookTitle;

    @Column(name = "book_isbn", nullable = false, length = 13, updatable = false)
    private String bookIsbn;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    /** Required by JPA. */
    protected OrderItem() {
    }

    public OrderItem(Long bookId, String bookTitle, String bookIsbn, BigDecimal unitPrice, int quantity) {
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.bookIsbn = bookIsbn;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
