package com.example.ebookstore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.example.ebookstore.domain.RecommendationStrategy;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.RecommendationList;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Category;
import com.example.ebookstore.entity.Publisher;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.service.impl.RecommendationServiceImpl;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMapper bookMapper;

    @Mock
    private Book book1;
    @Mock
    private Book book2;
    @Mock
    private Category category;
    @Mock
    private Publisher publisher;
    @Mock
    private Author author;

    private RecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecommendationServiceImpl(orderRepository, bookRepository, bookMapper);
    }

    @Test
    void userWithNoOrdersFallsBackToPopularStrategy() {
        when(orderRepository.findPurchasedBookIdsByUserId(1L)).thenReturn(List.of());
        when(bookRepository.findPopular(any(Pageable.class))).thenReturn(List.of(book1));
        BookSummary summary = new BookSummary(1L, "Popular Book", List.of("Author"), "9781234567890",
                new BigDecimal("500.00"), null, new BigDecimal("4.50"), 100, true, 1L, "Cat", 1L, "Pub", 2021);
        when(bookMapper.toSummary(book1)).thenReturn(summary);

        RecommendationList result = service.getRecommendations(1L, 5);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.POPULAR);
        assertThat(result.books()).containsExactly(summary);
        verify(bookRepository).findPopular(any(Pageable.class));
    }

    @Test
    void userWithPurchasedBooksReturnsPurchaseHistoryStrategy() {
        when(orderRepository.findPurchasedBookIdsByUserId(1L)).thenReturn(List.of(10L));
        when(book1.getCategory()).thenReturn(category);
        when(category.getId()).thenReturn(2L);
        when(book1.getPublisher()).thenReturn(publisher);
        when(publisher.getId()).thenReturn(3L);
        when(book1.getAuthors()).thenReturn(Set.of(author));
        when(author.getId()).thenReturn(4L);

        when(bookRepository.findAllByIdWithDetails(List.of(10L))).thenReturn(List.of(book1));
        when(bookRepository.findRecommendations(eq(List.of(10L)), eq(Set.of(2L)), eq(Set.of(3L)), eq(Set.of(4L)), any(Pageable.class)))
                .thenReturn(List.of(book2));

        BookSummary summary = new BookSummary(20L, "Recommended Book", List.of("Author"), "9781234567891",
                new BigDecimal("600.00"), null, new BigDecimal("4.80"), 120, true, 2L, "Cat", 3L, "Pub", 2022);
        when(bookMapper.toSummary(book2)).thenReturn(summary);

        RecommendationList result = service.getRecommendations(1L, 10);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.PURCHASE_HISTORY);
        assertThat(result.books()).containsExactly(summary);
    }

    @Test
    void purchaseHistoryYieldingNoResultsFallsBackToPopularExcludingPurchased() {
        when(orderRepository.findPurchasedBookIdsByUserId(1L)).thenReturn(List.of(10L));
        when(book1.getCategory()).thenReturn(category);
        when(category.getId()).thenReturn(2L);
        when(book1.getPublisher()).thenReturn(publisher);
        when(publisher.getId()).thenReturn(3L);
        when(book1.getAuthors()).thenReturn(Set.of(author));
        when(author.getId()).thenReturn(4L);

        when(bookRepository.findAllByIdWithDetails(List.of(10L))).thenReturn(List.of(book1));
        when(bookRepository.findRecommendations(eq(List.of(10L)), eq(Set.of(2L)), eq(Set.of(3L)), eq(Set.of(4L)), any(Pageable.class)))
                .thenReturn(List.of());

        when(bookRepository.findPopularExcluding(eq(List.of(10L)), any(Pageable.class))).thenReturn(List.of(book2));

        BookSummary summary = new BookSummary(20L, "Popular Fallback", List.of("Author"), "9781234567892",
                new BigDecimal("450.00"), null, new BigDecimal("4.60"), 80, true, 2L, "Cat", 3L, "Pub", 2020);
        when(bookMapper.toSummary(book2)).thenReturn(summary);

        RecommendationList result = service.getRecommendations(1L, 10);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.POPULAR);
        assertThat(result.books()).containsExactly(summary);
    }
}
