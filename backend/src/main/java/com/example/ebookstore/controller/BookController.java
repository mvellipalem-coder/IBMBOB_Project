package com.example.ebookstore.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.BookDetail;
import com.example.ebookstore.dto.BookPage;
import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSort;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.service.CatalogueService;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Public, read-only book endpoints (operations listBooks, searchBooks, getBook and getRelatedBooks in openapi.yaml).
 *
 * <p>There are deliberately no POST/PUT/DELETE handlers: the public API cannot change the catalogue.
 * The constraints on the parameters (for example {@code @Max(100)} on {@code size}) are checked by Spring
 * before the method runs; a violation becomes a 400 {@code VALIDATION_FAILED} response with {@code fieldErrors}.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final CatalogueService catalogueService;

    public BookController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /** {@code GET /api/books} - browse with filters, sorting and paging. */
    @GetMapping
    public BookPage listBooks(
            @RequestParam(required = false) @Min(1) Long categoryId,
            @RequestParam(required = false) @Min(1) Long publisherId,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogueService.findBooks(new BookSearchCriteria(null, categoryId, publisherId, minPrice, maxPrice,
                inStock, minRating, BookSort.fromParameter(sort, false), page, size));
    }

    /** {@code GET /api/books/search?q=...} - the same filters plus keyword search. */
    @GetMapping("/search")
    public BookPage searchBooks(
            @RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) @Min(1) Long categoryId,
            @RequestParam(required = false) @Min(1) Long publisherId,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogueService.findBooks(new BookSearchCriteria(q.trim(), categoryId, publisherId, minPrice,
                maxPrice, inStock, minRating, BookSort.fromParameter(sort, true), page, size));
    }

    /** {@code GET /api/books/{id}} - one book; 404 {@code BOOK_NOT_FOUND} when it does not exist. */
    @GetMapping("/{id}")
    public BookDetail getBook(@PathVariable @Min(1) long id) {
        return catalogueService.getBook(id);
    }

    /** {@code GET /api/books/{id}/related} - books that share an author, the category or the publisher. */
    @GetMapping("/{id}/related")
    public List<BookSummary> getRelatedBooks(@PathVariable @Min(1) long id,
                                             @RequestParam(defaultValue = "6") @Min(1) @Max(12) int limit) {
        return catalogueService.getRelatedBooks(id, limit);
    }
}
