package com.example.ebookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.entity.CustomerOrder;

/** Database access for orders. Lookups always include the user id, so one user can never read another's order. */
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    Page<CustomerOrder> findByUserId(Long userId, Pageable pageable);

    Page<CustomerOrder> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    /** Next value of the database sequence behind order numbers (atomic, safe under concurrency). */
    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    /** Distinct book IDs purchased in non-cancelled orders by the user. */
    @Query("""
            select distinct oi.bookId from CustomerOrder co join co.items oi
            where co.userId = :userId and co.status <> com.example.ebookstore.domain.OrderStatus.CANCELLED
            """)
    List<Long> findPurchasedBookIdsByUserId(@Param("userId") Long userId);
}
