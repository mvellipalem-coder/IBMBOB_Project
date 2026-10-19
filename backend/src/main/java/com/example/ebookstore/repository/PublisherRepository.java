package com.example.ebookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ebookstore.entity.Publisher;

/** Database access for publishers. Publishers are listed with {@code findAll(Sort)}. */
public interface PublisherRepository extends JpaRepository<Publisher, Long> {
}
