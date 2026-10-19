package com.example.ebookstore.repository;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSort;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Small, reusable query pieces ("Specifications") for the book catalogue.
 *
 * <p>Each method returns ONE optional condition. {@link #fromCriteria(BookSearchCriteria)} combines the ones
 * that the user actually asked for with AND, so there is no pile of {@code findByXAndYAndZ} methods and no
 * SQL built by joining strings. All values are sent to the database as parameters, never pasted into SQL.
 */
public final class BookSpecifications {

    private BookSpecifications() {
    }

    /** Builds the complete condition for a list or search request. Inactive books are always excluded. */
    public static Specification<Book> fromCriteria(BookSearchCriteria c) {
        Specification<Book> spec = Specification.allOf(
                isActive(),
                inCategory(c.categoryId()),
                fromPublisher(c.publisherId()),
                priceAtLeast(c.minPrice()),
                priceAtMost(c.maxPrice()),
                inStockOnly(c.inStock()),
                ratingAtLeast(c.minRating()),
                matchesText(c.query()));
        if (c.sort() == BookSort.RELEVANCE && hasText(c.query())) {
            spec = spec.and(orderByRelevance(c.query()));
        }
        return spec;
    }

    /** Soft-deleted books ({@code active = false}) are never visible. */
    public static Specification<Book> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Book> inCategory(Long categoryId) {
        // root.get("category").get("id") reads the foreign-key column; no join is needed.
        return categoryId == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Book> fromPublisher(Long publisherId) {
        return publisherId == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("publisher").get("id"), publisherId);
    }

    public static Specification<Book> priceAtLeast(BigDecimal minPrice) {
        return minPrice == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), minPrice);
    }

    public static Specification<Book> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), maxPrice);
    }

    /** {@code inStock = true} keeps only books with stock; {@code null} or {@code false} keeps everything. */
    public static Specification<Book> inStockOnly(Boolean inStock) {
        return !Boolean.TRUE.equals(inStock) ? Specification.unrestricted()
                : (root, query, cb) -> cb.greaterThan(root.<Integer>get("stockQuantity"), 0);
    }

    public static Specification<Book> ratingAtLeast(BigDecimal minRating) {
        return minRating == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("ratingAvg"), minRating);
    }

    /**
     * Case-insensitive "contains" search over the title, the ISBN and the author names.
     *
     * <p>The author test uses an {@code EXISTS (subquery)} instead of a join. A join would repeat a book once for
     * every matching author and would break paging and the total count.
     */
    public static Specification<Book> matchesText(String text) {
        if (!hasText(text)) {
            return Specification.unrestricted();
        }
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("title")), pattern, ESCAPE),
                cb.like(cb.lower(root.<String>get("isbn")), pattern, ESCAPE),
                authorMatches(root, query, cb, pattern));
    }

    /**
     * Orders search results: title matches first, then author matches, then ISBN-only matches; ties by title
     * and id. Only applied to the real query, never to the {@code count(*)} query Spring runs for the total.
     */
    private static Specification<Book> orderByRelevance(String text) {
        String pattern = likePattern(text);
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                Expression<Integer> rank = cb.<Integer>selectCase()
                        .when(cb.like(cb.lower(root.<String>get("title")), pattern, ESCAPE), 0)
                        .when(authorMatches(root, query, cb, pattern), 1)
                        .otherwise(2);
                query.orderBy(cb.asc(rank), cb.asc(cb.lower(root.<String>get("title"))), cb.asc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    private static Predicate authorMatches(Root<Book> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                           CriteriaBuilder cb, String pattern) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Book> subBook = sub.from(Book.class);
        Join<Book, Author> author = subBook.join("authors");
        sub.select(subBook.<Long>get("id")).where(
                cb.equal(subBook.get("id"), root.get("id")),
                cb.like(cb.lower(author.<String>get("name")), pattern, ESCAPE));
        return cb.exists(sub);
    }

    private static final char ESCAPE = '\\';

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Turns user text into a safe SQL LIKE pattern: {@code %}, {@code _} and {@code \} typed by the user are
     * escaped so they match themselves instead of acting as wildcards.
     */
    static String likePattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
