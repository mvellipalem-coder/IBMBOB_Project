package com.example.ebookstore.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.dto.BookSearchCriteria;
import com.example.ebookstore.dto.BookSort;
import com.example.ebookstore.entity.Book;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Repository integration test: the real {@link BookRepository} and {@link BookSpecifications} against a real
 * (embedded) PostgreSQL with the real Flyway schema. Each test runs in a transaction that is rolled back, so the
 * test books below disappear again.
 *
 * <p>Test books (all titles contain "Zorp" so searches are not disturbed by the 26 seed books):
 * <pre>
 *  name              price  stock rating category publisher authors
 *  A Zorp Alpha      100.00   5   4.80    C1       P1       Ann Writer
 *  B Zorp Beta       200.00   0   3.00    C1       P2       Bob Writer
 *  C Zorp Gamma      300.00   7   4.00    C2       P1       Ann Writer, Cy Writer
 *  D Zorp Delta 100%_Off 400.00 2 0.00    C2       P2       Dee Other
 *  E Zorp Echo       150.00   9   0.00    C1       P1       (inactive: must never be returned)
 *  F Aardvark Zorp   50.00    3   0.00    C1       P1       Delta Person
 * </pre>
 */
@SpringBootTest
@Transactional
class BookRepositoryIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
    }

    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private long c1;
    private long c2;
    private long p1;
    private long p2;
    private long a;
    private long b;
    private long c;
    private long d;
    private long e;
    private long f;

    @BeforeEach
    void insertTestBooks() {
        c1 = insertNamed("categories", "Zz Test Category 1");
        c2 = insertNamed("categories", "Zz Test Category 2");
        p1 = insertNamed("publishers", "Zz Test Publisher 1");
        p2 = insertNamed("publishers", "Zz Test Publisher 2");
        long ann = insertNamed("authors", "Ann Writer");
        long bob = insertNamed("authors", "Bob Writer");
        long cy = insertNamed("authors", "Cy Writer");
        long dee = insertNamed("authors", "Dee Other");
        long delta = insertNamed("authors", "Delta Person");

        a = insertBook("Zorp Alpha", "9780001000011", "100.00", 5, "4.80", true, c1, p1, ann);
        b = insertBook("Zorp Beta", "9780001000028", "200.00", 0, "3.00", true, c1, p2, bob);
        c = insertBook("Zorp Gamma", "9780001000035", "300.00", 7, "4.00", true, c2, p1, ann, cy);
        d = insertBook("Zorp Delta 100%_Off", "9780001000042", "400.00", 2, "0", true, c2, p2, dee);
        e = insertBook("Zorp Echo", "9780001000059", "150.00", 9, "0", false, c1, p1, ann);
        f = insertBook("Aardvark Zorp", "9780001000066", "50.00", 3, "0", true, c1, p1, delta);
    }

    // ------------------------------------------------------------------ listing, paging, sorting

    @Test
    void inactiveBooksAreNeverReturned() {
        assertThat(ids(search(null, null, null, null, null, null, null, BookSort.NEWEST, 0, 100)))
                .doesNotContain(e);
        assertThat(ids(search("echo", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20))).isEmpty();
    }

    @Test
    void theDatabaseReturnsOnlyTheRequestedPageAndTheCorrectTotals() {
        Page<Book> first = search("zorp", c1, null, null, null, null, null, BookSort.PRICE_ASC, 0, 2);
        Page<Book> second = search("zorp", c1, null, null, null, null, null, BookSort.PRICE_ASC, 1, 2);

        assertThat(ids(first)).containsExactly(f, a);
        assertThat(ids(second)).containsExactly(b);
        assertThat(first.getTotalElements()).isEqualTo(3); // the inactive book is not counted
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.isFirst()).isTrue();
        assertThat(second.isLast()).isTrue();
    }

    @Test
    void aPageBeyondTheLastPageIsEmptyButKeepsTheTotal() {
        Page<Book> page = search("zorp", c1, null, null, null, null, null, BookSort.PRICE_ASC, 5, 2);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void everySortOrderWorksAndIsStable() {
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(f, a, b, c, d);
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.PRICE_DESC, 0, 20)))
                .containsExactly(d, c, b, a, f);
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.TITLE_ASC, 0, 20)))
                .containsExactly(f, a, b, d, c);
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.TITLE_DESC, 0, 20)))
                .containsExactly(c, d, b, a, f);
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.RATING_DESC, 0, 20)))
                .containsExactly(a, c, b, d, f);
        // All rows were created in one transaction (same created_at), so the id breaks the tie: newest id first.
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.NEWEST, 0, 20)))
                .containsExactly(f, d, c, b, a);
    }

    // ------------------------------------------------------------------ filters

    @Test
    void filterByCategoryAndByPublisher() {
        assertThat(ids(search("zorp", c2, null, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(c, d);
        assertThat(ids(search("zorp", null, p2, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(b, d);
        assertThat(ids(search("zorp", c2, p1, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(c);
        assertThat(search(null, 999_999L, null, null, null, null, null, BookSort.NEWEST, 0, 20).getContent())
                .isEmpty();
    }

    @Test
    void priceRangeIsInclusiveAtBothEnds() {
        assertThat(ids(search("zorp", null, null, "100.00", "300.00", null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(a, b, c);
        assertThat(ids(search("zorp", null, null, "300", null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(c, d);
        assertThat(ids(search("zorp", null, null, null, "50", null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(f);
    }

    @Test
    void inStockTrueHidesSoldOutBooksAndFalseOrOmittedShowsAll() {
        assertThat(ids(search("zorp", null, null, null, null, true, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(f, a, c, d);
        assertThat(ids(search("zorp", null, null, null, null, false, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(f, a, b, c, d);
        assertThat(ids(search("zorp", null, null, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(f, a, b, c, d);
    }

    @Test
    void minimumRatingIsInclusive() {
        assertThat(ids(search("zorp", null, null, null, null, null, "4.00", BookSort.RATING_DESC, 0, 20)))
                .containsExactly(a, c);
    }

    @Test
    void allFiltersCombineWithAnd() {
        assertThat(ids(search("zorp", c1, p1, "60", "500", true, "4", BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(a);
    }

    // ------------------------------------------------------------------ search

    @Test
    void searchMatchesTitleCaseInsensitively() {
        assertThat(ids(search("ZoRp BeTa", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)))
                .containsExactly(b);
    }

    @Test
    void searchMatchesAuthorNamesAndNeverDuplicatesABook() {
        // Book C has TWO authors that contain "writer"; it must still appear exactly once and be counted once.
        Page<Book> page = search("writer", null, null, null, null, null, null, BookSort.PRICE_ASC, 0, 20);

        assertThat(ids(page)).containsExactly(a, b, c); // E is inactive
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(ids(search("ann writer", null, null, null, null, null, null, BookSort.PRICE_ASC, 0, 20)))
                .containsExactly(a, c);
    }

    @Test
    void searchMatchesIsbn() {
        assertThat(ids(search("9780001000035", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)))
                .containsExactly(c);
    }

    @Test
    void searchDoesNotMatchTheDescriptionOrThePublisherName() {
        assertThat(search("Zz Test Publisher", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)
                .getContent()).isEmpty();
    }

    @Test
    void relevanceRanksTitleMatchesBeforeAuthorMatches() {
        // "delta": book D has it in the TITLE, book F only in an AUTHOR name. F sorts first alphabetically,
        // so only a real relevance ranking can put D first.
        assertThat(ids(search("delta", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)))
                .containsExactly(d, f);
        assertThat(ids(search("delta", null, null, null, null, null, null, BookSort.TITLE_ASC, 0, 20)))
                .containsExactly(f, d);
    }

    @Test
    void searchTextIsNeverTreatedAsASqlWildcard() {
        // If % and _ were wildcards, "%_" would match every book.
        assertThat(ids(search("%_", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)))
                .containsExactly(d);
        assertThat(ids(search("100%", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20)))
                .containsExactly(d);
        assertThat(search("\\", null, null, null, null, null, null, BookSort.RELEVANCE, 0, 20).getContent())
                .isEmpty();
    }

    @Test
    void sqlInjectionTextIsJustTextAndMatchesNothing() {
        Page<Book> page = search("x' OR '1'='1'; DROP TABLE books;--", null, null, null, null, null, null,
                BookSort.RELEVANCE, 0, 20);

        assertThat(page.getContent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books", Long.class)).isGreaterThan(26);
    }

    // ------------------------------------------------------------------ details and related

    @Test
    void findByIdAndActiveTrueFindsActiveBooksOnly() {
        assertThat(bookRepository.findByIdAndActiveTrue(a)).isPresent();
        assertThat(bookRepository.findByIdAndActiveTrue(e)).isEmpty();
        assertThat(bookRepository.findByIdAndActiveTrue(-5L)).isEmpty();
    }

    @Test
    void relatedBooksRankSharedAuthorFirstThenCategoryThenPublisher() {
        Book base = bookRepository.findByIdAndActiveTrue(a).orElseThrow();
        List<Long> authorIds = base.getAuthors().stream().map(x -> x.getId()).toList();

        List<Book> related = bookRepository.findRelated(a, c1, p1, authorIds, PageRequest.of(0, 10));

        // C shares the author "Ann Writer"; F shares category AND publisher; B shares the category only.
        // The book itself (A) and the inactive E are never included. D (other category and publisher) is unrelated.
        assertThat(related).extracting(Book::getId).startsWith(c, f, b).doesNotContain(a, e, d);
        assertThat(bookRepository.findRelated(a, c1, p1, authorIds, PageRequest.of(0, 2)))
                .extracting(Book::getId).containsExactly(c, f);
    }

    // ------------------------------------------------------------------ helpers

    private Page<Book> search(String query, Long categoryId, Long publisherId, String minPrice, String maxPrice,
                              Boolean inStock, String minRating, BookSort sort, int page, int size) {
        BookSearchCriteria criteria = new BookSearchCriteria(query, categoryId, publisherId, decimal(minPrice),
                decimal(maxPrice), inStock, decimal(minRating), sort, page, size);
        return bookRepository.findAll(BookSpecifications.fromCriteria(criteria),
                PageRequest.of(page, size, sort.toSort()));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static List<Long> ids(Page<Book> page) {
        return page.getContent().stream().map(Book::getId).toList();
    }

    private long insertNamed(String table, String name) {
        return jdbc.queryForObject("INSERT INTO " + table + " (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private long insertBook(String title, String isbn, String price, int stock, String rating, boolean active,
                            long categoryId, long publisherId, long... authorIds) {
        long id = jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, rating_avg, rating_count, active,
                                   category_id, publisher_id)
                VALUES (?, ?, 'test', ?, ?, ?, 1, ?, ?, ?) RETURNING id
                """, Long.class, title, isbn, new BigDecimal(price), stock, new BigDecimal(rating), active,
                categoryId, publisherId);
        for (long authorId : authorIds) {
            jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", id, authorId);
        }
        return id;
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", ex);
        }
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }
}
