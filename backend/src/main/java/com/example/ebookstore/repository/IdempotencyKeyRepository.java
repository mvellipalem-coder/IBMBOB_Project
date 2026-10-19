package com.example.ebookstore.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.IdempotencyKey;

/** Database access for remembered idempotency keys. Always scoped to one user and one operation. */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndOperationAndKeyValue(Long userId, String operation, String keyValue);

    /** Forgets this one key if it is older than its time-to-live, so the key can be used again. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from IdempotencyKey k
            where k.userId = :userId and k.operation = :operation and k.keyValue = :keyValue and k.expiresAt <= :now
            """)
    int deleteIfExpired(@Param("userId") Long userId, @Param("operation") String operation,
                        @Param("keyValue") String keyValue, @Param("now") Instant now);
}
