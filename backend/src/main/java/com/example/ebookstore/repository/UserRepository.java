package com.example.ebookstore.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ebookstore.entity.User;

/** Database access for users. Spring Data generates safe, parameterised SQL from the method names. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code email} must already be lower case (see {@code AuthServiceImpl}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
