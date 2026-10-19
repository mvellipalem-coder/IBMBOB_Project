package com.example.ebookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.CartItem;

/**
 * Database access for cart lines. Lookups always include the cart id, so a line can only be found through the
 * cart of the user who owns it. The entity graph loads book, category and publisher in the same query (authors
 * follow in batches, see {@code @BatchSize} on {@code Book}).
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"book", "book.category", "book.publisher"})
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    @EntityGraph(attributePaths = {"book", "book.category", "book.publisher"})
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    @EntityGraph(attributePaths = {"book", "book.category", "book.publisher"})
    Optional<CartItem> findByCartIdAndBookId(Long cartId, Long bookId);

    /** Just the book id and quantity of a line (no book data), as checkout needs them. */
    interface CartLine {
        Long getBookId();

        int getQuantity();
    }

    /**
     * The lines of a cart without loading any {@code Book}. Checkout locks the books afterwards and must see their
     * current rows, not copies that were loaded earlier in the same transaction.
     */
    @Query("select ci.book.id as bookId, ci.quantity as quantity from CartItem ci where ci.cartId = :cartId order by ci.id")
    List<CartLine> findLinesByCartId(@Param("cartId") Long cartId);

    /** Empties a cart (after a successful checkout). */
    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem ci where ci.cartId = :cartId")
    int deleteAllByCartId(@Param("cartId") Long cartId);
}
