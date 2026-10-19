package com.example.ebookstore.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.domain.RecommendationStrategy;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.RecommendationList;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.service.RecommendationService;

/**
 * Rule-based recommendation engine.
 *
 * <p>Recommends active, in-stock books from categories, authors, and publishers of the user's
 * previous non-cancelled orders, excluding already purchased books. Falls back to top-rated
 * popular books for users without qualifying order history or when purchase history yields no candidates.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationServiceImpl implements RecommendationService {

    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public RecommendationServiceImpl(OrderRepository orderRepository, BookRepository bookRepository,
                                      BookMapper bookMapper) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.bookMapper = bookMapper;
    }

    @Override
    public RecommendationList getRecommendations(Long userId, int limit) {
        List<Long> purchasedBookIds = orderRepository.findPurchasedBookIdsByUserId(userId);

        if (purchasedBookIds.isEmpty()) {
            return fallbackToPopular(purchasedBookIds, limit);
        }

        List<Book> purchasedBooks = bookRepository.findAllByIdWithDetails(purchasedBookIds);
        if (purchasedBooks.isEmpty()) {
            return fallbackToPopular(purchasedBookIds, limit);
        }

        Set<Long> categoryIds = purchasedBooks.stream()
                .map(b -> b.getCategory().getId())
                .collect(Collectors.toSet());
        Set<Long> publisherIds = purchasedBooks.stream()
                .map(b -> b.getPublisher().getId())
                .collect(Collectors.toSet());
        Set<Long> authorIds = purchasedBooks.stream()
                .flatMap(b -> b.getAuthors().stream())
                .map(Author::getId)
                .collect(Collectors.toSet());

        List<Book> recommended = bookRepository.findRecommendations(
                purchasedBookIds,
                categoryIds.isEmpty() ? Set.of(-1L) : categoryIds,
                publisherIds.isEmpty() ? Set.of(-1L) : publisherIds,
                authorIds.isEmpty() ? Set.of(-1L) : authorIds,
                PageRequest.of(0, limit, Sort.unsorted())
        );

        if (recommended.isEmpty()) {
            return fallbackToPopular(purchasedBookIds, limit);
        }

        List<BookSummary> summaries = recommended.stream().map(bookMapper::toSummary).toList();
        return new RecommendationList(RecommendationStrategy.PURCHASE_HISTORY, summaries);
    }

    private RecommendationList fallbackToPopular(Collection<Long> excludedBookIds, int limit) {
        List<Book> popular;
        if (excludedBookIds != null && !excludedBookIds.isEmpty()) {
            popular = bookRepository.findPopularExcluding(excludedBookIds, PageRequest.of(0, limit, Sort.unsorted()));
            if (popular.isEmpty()) {
                popular = bookRepository.findPopular(PageRequest.of(0, limit, Sort.unsorted()));
            }
        } else {
            popular = bookRepository.findPopular(PageRequest.of(0, limit, Sort.unsorted()));
        }

        List<BookSummary> summaries = popular.stream().map(bookMapper::toSummary).toList();
        return new RecommendationList(RecommendationStrategy.POPULAR, summaries);
    }
}
