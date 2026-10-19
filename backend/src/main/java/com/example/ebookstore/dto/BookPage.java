package com.example.ebookstore.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of books (schema {@code BookPage}): the paging information followed by the books.
 * Spring's own {@code Page} is not returned directly because its JSON shape differs from the contract.
 */
public record BookPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<BookSummary> content) {

    /** Builds the response from a page of already-converted DTOs. */
    public static BookPage of(Page<BookSummary> page) {
        return new BookPage(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast(), page.getContent());
    }
}
