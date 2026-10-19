package com.example.ebookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ebookstore.entity.Category;

/** Database access for categories. Categories are listed with {@code findAll(Sort)}. */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
