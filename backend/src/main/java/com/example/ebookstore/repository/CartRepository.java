package com.example.ebookstore.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.Cart;

import jakarta.persistence.LockModeType;

/** Database access for carts. Every query is parameterised and scoped to one user id taken from the token. */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /**
     * The user's cart, locked ({@code SELECT ... FOR UPDATE}) until the surrounding transaction ends. Two
     * simultaneous changes to the same cart therefore run one after the other, so the stock and per-line limit
     * checks cannot be bypassed by racing requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    /** Creates the cart on first use. Does nothing when it already exists, even under concurrency. */
    @Modifying
    @Query(value = "INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId);
}
