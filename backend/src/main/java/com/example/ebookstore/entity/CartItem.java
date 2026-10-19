package com.example.ebookstore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line of a cart (table {@code cart_items}): a book and how many copies.
 *
 * <p>No price is stored here on purpose. Prices always come from the catalogue ({@link Book#getPrice()}) when the
 * cart is read, so a client can never influence them. The database guarantees one line per book per cart.
 * This class is never returned from a controller.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private Long cartId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Book book;

    @Column(nullable = false)
    private int quantity;

    /** Required by JPA. */
    protected CartItem() {
    }

    public CartItem(Long cartId, Book book, int quantity) {
        this.cartId = cartId;
        this.book = book;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Book getBook() {
        return book;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
