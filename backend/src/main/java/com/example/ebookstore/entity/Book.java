package com.example.ebookstore.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A book for sale (table {@code books}). Books are never hard-deleted: {@code active = false} hides them.
 *
 * <p>Notes for beginners:
 * <ul>
 *   <li>Associations are LAZY: related rows are only loaded when needed.</li>
 *   <li>{@code ratingAvg}, {@code ratingCount} and {@code updatedAt} are maintained by database triggers,
 *       so JPA must never write them ({@code insertable = false, updatable = false}).</li>
 *   <li>The public catalogue is read-only, so this class has no setters.</li>
 *   <li>This class is never returned from a controller; {@code BookSummary}/{@code BookDetail} are used.</li>
 * </ul>
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 13)
    private String isbn;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2, insertable = false, updatable = false)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    /** {@code @BatchSize} loads the authors of up to 50 books in ONE query instead of one query per book. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "book_authors",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    @BatchSize(size = 50)
    private Set<Author> authors = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Book() {
    }

    /**
     * Takes copies out of stock for an order. Call it only on a row that is locked for update
     * (see {@code BookRepository#findAllByIdForUpdate}); the check here is the last safety net.
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0 || quantity > stockQuantity) {
            throw new IllegalStateException("Cannot take " + quantity + " copies from stock of " + stockQuantity);
        }
        stockQuantity -= quantity;
    }

    /**
     * Puts copies back into stock (e.g. when an order is cancelled or a payment fails).
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        stockQuantity += quantity;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public boolean isActive() {
        return active;
    }

    public Category getCategory() {
        return category;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public Set<Author> getAuthors() {
        return authors;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
