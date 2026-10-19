package com.example.ebookstore.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.User;

import jakarta.persistence.LockModeType;

/** Database access for users. Spring Data generates safe, parameterised SQL from the method names. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code email} must already be lower case (see {@code AuthServiceImpl}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * The user's row, locked ({@code SELECT ... FOR UPDATE}) until the transaction ends. Used as the per-user
     * "mutex" for everything that must not run twice at once for one user: address changes, checkouts, and so
     * spending gift points. After the lock is granted, {@link #findPointsBalance} reads the committed balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * The gift-point balance, read straight from the database (the {@code users.points_balance} column that the
     * ledger trigger maintains). A native query is used because the column is deliberately not mapped on the entity,
     * so it can never be changed from Java.
     */
    @Query(value = "select points_balance from users where id = :id", nativeQuery = true)
    Optional<Integer> findPointsBalance(@Param("id") Long id);

    /**
     * Limits how long this transaction waits for a row lock (PostgreSQL {@code lock_timeout}, in milliseconds,
     * for the current transaction only). Waiting forever is never acceptable; see the security policy.
     */
    @Query(value = "select set_config('lock_timeout', cast(:millis as text), true)", nativeQuery = true)
    String setLocalLockTimeout(@Param("millis") int millis);
}
