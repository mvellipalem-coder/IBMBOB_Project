package com.example.ebookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ebookstore.entity.Address;

/** Database access for addresses. Every lookup includes the user id, so another user's address is never found. */
public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Default address first, then newest first (the order the API promises). */
    List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** The most recently created address of the user (used to pick a new default after a delete). */
    Optional<Address> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Clears the default flag of all the user's addresses. */
    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    int clearDefault(@Param("userId") Long userId);

    /** Clears the default flag of all the user's addresses except one. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Address a set a.defaultAddress = false
            where a.userId = :userId and a.defaultAddress = true and a.id <> :exceptId
            """)
    int clearDefaultExcept(@Param("userId") Long userId, @Param("exceptId") Long exceptId);
}
