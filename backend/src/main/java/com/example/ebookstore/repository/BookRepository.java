package com.example.ebookstore.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.Book;

import jakarta.persistence.LockModeType;

/**
 * Database access for books.
 *
 * <p>Filters, search and sorting are built with {@link BookSpecifications} (Spring Data "Specifications") and
 * run through {@link #findAll(Specification, Pageable)}, which asks the database for ONE page at a time.
 * The whole catalogue is never loaded into memory.
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /**
     * The given books, locked ({@code SELECT ... FOR UPDATE}) in ascending id order until the transaction ends.
     * Checkout uses it so that two simultaneous orders cannot both take the last copy. The fixed order means two
     * checkouts that share several books can never wait on each other in a circle (deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id in :ids order by b.id")
    List<Book> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** An active book by id, with its category and publisher loaded in the same query. */
    @EntityGraph(attributePaths = {"category", "publisher"})
    Optional<Book> findByIdAndActiveTrue(Long id);

    /**
     * One page of books that match the specification. The entity graph loads category and publisher with the
     * books (one query instead of one per book). The authors are loaded afterwards in batches of 50
     * (see {@code @BatchSize} on {@link Book#getAuthors()}), which is safe together with paging.
     */
    @Override
    @EntityGraph(attributePaths = {"category", "publisher"})
    Page<Book> findAll(Specification<Book> spec, Pageable pageable);

    /**
     * Active books that share an author, the category or the publisher with another book, best match first.
     *
     * <p>Ranking: same author, then same category, then same publisher only. Within a rank, books that also share
     * the publisher come first, then better rated books; the id makes the order stable. The database applies
     * the limit through {@code pageable} (for example {@code PageRequest.of(0, 6)}), so at most that many rows are read.
     *
     * @param authorIds must not be empty (an empty {@code IN ()} is invalid SQL)
     */
    @EntityGraph(attributePaths = {"category", "publisher"})
    @Query("""
            select b from Book b
            where b.active = true
              and b.id <> :bookId
              and (b.category.id = :categoryId
                   or b.publisher.id = :publisherId
                   or b.id in (select sa.id from Book sa join sa.authors a where a.id in :authorIds))
            order by
              case when b.id in (select sa2.id from Book sa2 join sa2.authors a2 where a2.id in :authorIds) then 0
                   when b.category.id = :categoryId then 1
                   else 2 end,
              case when b.publisher.id = :publisherId then 0 else 1 end,
              b.ratingAvg desc, b.ratingCount desc, b.id asc
            """)
    List<Book> findRelated(@Param("bookId") Long bookId,
                           @Param("categoryId") Long categoryId,
                           @Param("publisherId") Long publisherId,
                           @Param("authorIds") Collection<Long> authorIds,
                           Pageable pageable);

    /** Active/purchased books by IDs with their category and publisher eagerly loaded. */
    @EntityGraph(attributePaths = {"category", "publisher"})
    @Query("select b from Book b where b.id in :ids")
    List<Book> findAllByIdWithDetails(@Param("ids") Collection<Long> ids);

    /**
     * Active in-stock books matching category, publisher, or author from previous orders, excluding already purchased books.
     */
    @EntityGraph(attributePaths = {"category", "publisher"})
    @Query("""
            select b from Book b
            where b.active = true
              and b.stockQuantity > 0
              and b.id not in :excludedBookIds
              and (b.category.id in :categoryIds
                   or b.publisher.id in :publisherIds
                   or b.id in (select ba.id from Book ba join ba.authors a where a.id in :authorIds))
            order by
              case when b.id in (select ba2.id from Book ba2 join ba2.authors a2 where a2.id in :authorIds) then 0
                   when b.category.id in :categoryIds then 1
                   else 2 end,
              case when b.publisher.id in :publisherIds then 0 else 1 end,
              b.ratingAvg desc, b.ratingCount desc, b.id asc
            """)
    List<Book> findRecommendations(@Param("excludedBookIds") Collection<Long> excludedBookIds,
                                   @Param("categoryIds") Collection<Long> categoryIds,
                                   @Param("publisherIds") Collection<Long> publisherIds,
                                   @Param("authorIds") Collection<Long> authorIds,
                                   Pageable pageable);

    /** Top rated active in-stock books for popular fallback. */
    @EntityGraph(attributePaths = {"category", "publisher"})
    @Query("""
            select b from Book b
            where b.active = true
              and b.stockQuantity > 0
            order by b.ratingAvg desc, b.ratingCount desc, b.id asc
            """)
    List<Book> findPopular(Pageable pageable);

    /** Top rated active in-stock books excluding specified IDs for fallback. */
    @EntityGraph(attributePaths = {"category", "publisher"})
    @Query("""
            select b from Book b
            where b.active = true
              and b.stockQuantity > 0
              and b.id not in :excludedBookIds
            order by b.ratingAvg desc, b.ratingCount desc, b.id asc
            """)
    List<Book> findPopularExcluding(@Param("excludedBookIds") Collection<Long> excludedBookIds, Pageable pageable);
}
