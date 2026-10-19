package com.example.ebookstore.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.dto.ApiFieldError;
import com.example.ebookstore.dto.BookDetail;
import com.example.ebookstore.dto.BookPage;
import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.CategoryResponse;
import com.example.ebookstore.dto.PublisherResponse;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.BookSpecifications;
import com.example.ebookstore.repository.CategoryRepository;
import com.example.ebookstore.repository.PublisherRepository;
import com.example.ebookstore.service.CatalogueService;

/**
 * Catalogue use cases. Every method only reads, so the whole class is {@code readOnly} (a hint that lets the
 * database and JPA skip work needed for changes).
 */
@Service
@Transactional(readOnly = true)
public class CatalogueServiceImpl implements CatalogueService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final PublisherRepository publisherRepository;
    private final BookMapper mapper;

    public CatalogueServiceImpl(BookRepository bookRepository, CategoryRepository categoryRepository,
                                PublisherRepository publisherRepository, BookMapper mapper) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.publisherRepository = publisherRepository;
        this.mapper = mapper;
    }

    @Override
    public BookPage findBooks(BookSearchCriteria criteria) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                    List.of(new ApiFieldError("minPrice", "must not be greater than maxPrice")));
        }
        // The database returns only the requested page (LIMIT/OFFSET), never the whole catalogue.
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), criteria.sort().toSort());
        Page<Book> books = bookRepository.findAll(BookSpecifications.fromCriteria(criteria), pageable);
        return BookPage.of(books.map(mapper::toSummary));
    }

    @Override
    public BookDetail getBook(long id) {
        return mapper.toDetail(findActiveBook(id));
    }

    @Override
    public List<BookSummary> getRelatedBooks(long id, int limit) {
        Book book = findActiveBook(id);
        Set<Long> authorIds = book.getAuthors().stream().map(Author::getId).collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            // An empty IN () list is invalid SQL; -1 matches no author. Category/publisher still apply.
            authorIds = Set.of(-1L);
        }
        // Sorting is part of the query itself, so the Pageable only carries the limit.
        List<Book> related = bookRepository.findRelated(book.getId(), book.getCategory().getId(),
                book.getPublisher().getId(), authorIds, PageRequest.of(0, limit, Sort.unsorted()));
        return related.stream().map(mapper::toSummary).toList();
    }

    @Override
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<PublisherResponse> listPublishers() {
        return publisherRepository.findAll(Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
                .stream().map(mapper::toResponse).toList();
    }

    private Book findActiveBook(long id) {
        return bookRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }
}
