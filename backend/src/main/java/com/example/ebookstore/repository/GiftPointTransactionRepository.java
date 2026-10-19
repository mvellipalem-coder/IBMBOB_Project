package com.example.ebookstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.domain.GiftPointTransactionType;
import com.example.ebookstore.entity.GiftPointTransaction;

/**
 * Database access for the gift-point ledger. Reads are always scoped to one user. The only write is
 * {@link #insertRedeem}: the database trigger then updates the user's balance and fills {@code balance_after}, and
 * refuses the whole insert when the balance would drop below zero.
 */
public interface GiftPointTransactionRepository extends JpaRepository<GiftPointTransaction, Long> {

    Page<GiftPointTransaction> findByUserId(Long userId, Pageable pageable);

    Page<GiftPointTransaction> findByUserIdAndType(Long userId, GiftPointTransactionType type, Pageable pageable);

    /**
     * Appends a {@code REDEEM} entry. {@code points} must be negative (the table checks the sign). The value 0 for
     * {@code balance_after} is only a placeholder: the trigger overwrites it with the real running balance.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO gift_point_transactions (user_id, order_id, type, points, balance_after, description)
            VALUES (:userId, :orderId, 'REDEEM', :points, 0, :description)
            """, nativeQuery = true)
    int insertRedeem(@Param("userId") Long userId, @Param("orderId") Long orderId,
                     @Param("points") int points, @Param("description") String description);

    /**
     * Appends a {@code REFUND} entry. {@code points} must be positive (the table checks the sign). The value 0 for
     * {@code balance_after} is only a placeholder: the trigger overwrites it with the real running balance.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO gift_point_transactions (user_id, order_id, type, points, balance_after, description)
            VALUES (:userId, :orderId, 'REFUND', :points, 0, :description)
            """, nativeQuery = true)
    int insertRefund(@Param("userId") Long userId, @Param("orderId") Long orderId,
                     @Param("points") int points, @Param("description") String description);
}
