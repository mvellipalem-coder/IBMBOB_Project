package com.example.ebookstore.service;

import java.util.List;

import com.example.ebookstore.dto.BookDetail;
import com.example.ebookstore.dto.BookPage;
import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.CategoryResponse;
import com.example.ebookstore.dto.PublisherResponse;

/** Read-only use cases of the book catalogue. Nothing here changes data. */
public interface CatalogueService {

    /** One page of active books matching the criteria (list or, when {@code query} is set, search). */
    BookPage findBooks(BookSearchCriteria criteria);

    /** @throws com.example.ebookstore.exception.ApiException {@code BOOK_NOT_FOUND} if missing or inactive */
    BookDetail getBook(long id);

    /**
     * Books related to the given one (same author, category or publisher), best match first.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code BOOK_NOT_FOUND} if missing or inactive
     */
    List<BookSummary> getRelatedBooks(long id, int limit);

    List<CategoryResponse> listCategories();

    List<PublisherResponse> listPublishers();
}
