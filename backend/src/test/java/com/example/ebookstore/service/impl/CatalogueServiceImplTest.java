package com.example.ebookstore.service.impl;

import static com.example.ebookstore.testsupport.CatalogueTestData.author;
import static com.example.ebookstore.testsupport.CatalogueTestData.book;
import static com.example.ebookstore.testsupport.CatalogueTestData.category;
import static com.example.ebookstore.testsupport.CatalogueTestData.publisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.BookDetail;
import com.example.ebookstore.dto.BookPage;
import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSort;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Category;
import com.example.ebookstore.entity.Publisher;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CategoryRepository;
import com.example.ebookstore.repository.PublisherRepository;

/** Unit tests of the catalogue rules. The repositories are mocks, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class CatalogueServiceImplTest {

    private static final BusinessProperties BUSINESS = new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
            new BigDecimal("10.00"), 48, 30, 24, 5, 2, List.of());

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private PublisherRepository publisherRepository;

    private CatalogueServiceImpl service;

    private final Category programming = category(4, "Programming");
    private final Publisher pearson = publisher(6, "Pearson");
    private final Author martin = author(1, "Robert C. Martin");
    private final Author fowler = author(2, "Martin Fowler");

    @BeforeEach
    void setUp() {
        service = new CatalogueServiceImpl(bookRepository, categoryRepository, publisherRepository,
                new BookMapper(BUSINESS));
    }

    private static BookSearchCriteria criteria(BigDecimal min, BigDecimal max) {
        return new BookSearchCriteria(null, null, null, min, max, null, null, BookSort.NEWEST, 0, 20);
    }

    // ------------------------------------------------------------------ list / search

    @Test
    void findBooksMapsAPageOfEntitiesToDtosAndKeepsThePagingInformation() {
        Book cleanCode = book(9, "Clean Code", "899.00", 30, programming, pearson, martin, fowler);
        Pageable requested = PageRequest.of(1, 2, BookSort.PRICE_ASC.toSort());
        when(bookRepository.findAll(any(Specification.class), eq(requested)))
                .thenReturn(new PageImpl<>(List.of(cleanCode), requested, 3));

        BookPage result = service.findBooks(new BookSearchCriteria(null, null, null, null, null, null, null,
                BookSort.PRICE_ASC, 1, 2));

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
        BookSummary summary = result.content().get(0);
        assertThat(summary.id()).isEqualTo(9);
        assertThat(summary.authors()).containsExactly("Martin Fowler", "Robert C. Martin");
        assertThat(summary.inStock()).isTrue();
        assertThat(summary.categoryName()).isEqualTo("Programming");
        assertThat(summary.publisherName()).isEqualTo("Pearson");
    }

    @Test
    void findBooksAsksTheDatabaseForExactlyOnePageWithTheRequestedSort() {
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.findBooks(new BookSearchCriteria(null, null, null, null, null, null, null, BookSort.TITLE_DESC, 3, 7));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(7);
        assertThat(captor.getValue().getSort()).isEqualTo(BookSort.TITLE_DESC.toSort());
        verify(bookRepository, never()).findAll();
    }

    @Test
    void findBooksReturnsAnEmptyPageWhenNothingMatches() {
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        BookPage result = service.findBooks(criteria(null, null));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void findBooksRejectsAMinimumPriceAboveTheMaximumPrice() {
        assertThatThrownBy(() -> service.findBooks(criteria(new BigDecimal("500"), new BigDecimal("100"))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getFieldErrors()).extracting("field").containsExactly("minPrice");
                });
        verifyNoInteractions(bookRepository);
    }

    @Test
    void findBooksAcceptsEqualMinimumAndMaximumPrice() {
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        BookPage result = service.findBooks(criteria(new BigDecimal("500.00"), new BigDecimal("500")));

        assertThat(result.content()).isEmpty();
    }

    // ------------------------------------------------------------------ details

    @Test
    void getBookReturnsTheDetailWithDescriptionAndMaxOrderableQuantity() {
        when(bookRepository.findByIdAndActiveTrue(9L))
                .thenReturn(Optional.of(book(9, "Clean Code", "899.00", 30, programming, pearson, martin)));

        BookDetail detail = service.getBook(9);

        assertThat(detail.title()).isEqualTo("Clean Code");
        assertThat(detail.description()).isEqualTo("About Clean Code");
        assertThat(detail.price()).isEqualByComparingTo("899.00");
        assertThat(detail.maxOrderableQuantity()).isEqualTo(10); // stock 30, per-line limit 10
    }

    @Test
    void maxOrderableQuantityIsTheStockWhenLowerThanTheLimitAndZeroWhenOutOfStock() {
        when(bookRepository.findByIdAndActiveTrue(1L))
                .thenReturn(Optional.of(book(1, "Few left", "100.00", 3, programming, pearson, martin)));
        when(bookRepository.findByIdAndActiveTrue(2L))
                .thenReturn(Optional.of(book(2, "Sold out", "100.00", 0, programming, pearson, martin)));

        assertThat(service.getBook(1).maxOrderableQuantity()).isEqualTo(3);
        BookDetail soldOut = service.getBook(2);
        assertThat(soldOut.maxOrderableQuantity()).isZero();
        assertThat(soldOut.inStock()).isFalse();
    }

    @Test
    void getBookThrowsBookNotFoundWhenMissingOrInactive() {
        when(bookRepository.findByIdAndActiveTrue(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBook(404))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    // ------------------------------------------------------------------ related

    @Test
    @SuppressWarnings("unchecked")
    void relatedBooksUseTheCategoryPublisherAndAuthorsOfTheBookAndTheRequestedLimit() {
        Book clean = book(9, "Clean Code", "899.00", 30, programming, pearson, martin, fowler);
        Book other = book(10, "Refactoring", "799.00", 20, programming, pearson, fowler);
        when(bookRepository.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(clean));
        when(bookRepository.findRelated(eq(9L), eq(4L), eq(6L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(other));

        List<BookSummary> related = service.getRelatedBooks(9, 4);

        assertThat(related).extracting(BookSummary::id).containsExactly(10L);
        ArgumentCaptor<java.util.Collection<Long>> authorIds = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findRelated(eq(9L), eq(4L), eq(6L), authorIds.capture(), pageable.capture());
        assertThat(authorIds.getValue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(4);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @SuppressWarnings("unchecked")
    void relatedBooksStillWorkForABookWithoutAuthors() {
        when(bookRepository.findByIdAndActiveTrue(5L))
                .thenReturn(Optional.of(book(5, "Anonymous", "100.00", 5, programming, pearson)));
        when(bookRepository.findRelated(eq(5L), eq(4L), eq(6L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.getRelatedBooks(5, 6)).isEmpty();

        ArgumentCaptor<java.util.Collection<Long>> authorIds = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(bookRepository).findRelated(eq(5L), eq(4L), eq(6L), authorIds.capture(), any(Pageable.class));
        assertThat(authorIds.getValue()).isNotEmpty(); // an empty IN () would be invalid SQL
    }

    @Test
    void relatedBooksOfAMissingBookThrowsBookNotFound() {
        when(bookRepository.findByIdAndActiveTrue(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRelatedBooks(404, 6))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
        verify(bookRepository, never()).findRelated(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ categories and publishers

    @Test
    void categoriesAndPublishersAreMappedAndSortedByName() {
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(programming));
        when(publisherRepository.findAll(any(Sort.class))).thenReturn(List.of(pearson));

        assertThat(service.listCategories()).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(4);
                    assertThat(c.name()).isEqualTo("Programming");
                });
        assertThat(service.listPublishers()).singleElement()
                .satisfies(p -> {
                    assertThat(p.id()).isEqualTo(6);
                    assertThat(p.description()).isNull();
                });

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(categoryRepository).findAll(sort.capture());
        assertThat(sort.getValue().getOrderFor("name")).isNotNull();
    }
}
