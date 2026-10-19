package com.example.ebookstore.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Integration tests for catalogue REST endpoints (/api/books, /api/books/search, /api/books/{id},
 * /api/books/{id}/related, /api/categories, /api/publishers).
 *
 * <p>All endpoints are public (no authentication required). Each test inserts its own books with a unique
 * title prefix so seed data does not interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogueControllerIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();
    // Each test class uses a unique ISBN range so concurrent test suites do not conflict.
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9786000000000L);

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private long categoryId;
    private long publisherId;
    private long authorId;
    private long bookA;
    private long bookB;
    private long inactiveBook;

    @BeforeEach
    void setUp() {
        categoryId = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        publisherId = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        authorId = jdbc.queryForObject("""
                INSERT INTO authors (name) VALUES ('CatTest Author') ON CONFLICT (lower(name))
                DO UPDATE SET name = EXCLUDED.name RETURNING id
                """, Long.class);
        bookA = insertBook("CatTestBook Alpha", "1200.00", 10, true);
        bookB = insertBook("CatTestBook Beta",  "800.00",  5,  true);
        inactiveBook = insertBook("CatTestBook Inactive", "500.00", 3, false);
    }

    // ------------------------------------------------------------------ GET /api/books

    @Test
    void listBooksReturnsActiveBooksWithPaginationAndDoesNotRequireAuth() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listBooksNeverReturnsInactiveBooks() throws Exception {
        mockMvc.perform(get("/api/books").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + inactiveBook + ")]").isEmpty());
    }

    @Test
    void listBooksFiltersOnPriceRange() throws Exception {
        mockMvc.perform(get("/api/books")
                        .param("minPrice", "1100")
                        .param("maxPrice", "1300")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + bookA + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id==" + bookB + ")]").isEmpty());
    }

    @Test
    void listBooksFiltersByCategoryId() throws Exception {
        mockMvc.perform(get("/api/books").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listBooksInStockFilterHidesSoldOutBooks() throws Exception {
        long soldOut = insertBook("CatTestBook SoldOut", "500.00", 0, true);

        mockMvc.perform(get("/api/books").param("inStock", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + soldOut + ")]").isEmpty());
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/books").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void tooLargePageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/books").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void negativePriceReturns400() throws Exception {
        mockMvc.perform(get("/api/books").param("minPrice", "-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ GET /api/books/search

    @Test
    void searchBooksReturnsBooksMatchingTitle() throws Exception {
        mockMvc.perform(get("/api/books/search").param("q", "CatTestBook Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + bookA + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id==" + bookB + ")]").isEmpty());
    }

    @Test
    void searchBooksTooShortQueryReturns400() throws Exception {
        mockMvc.perform(get("/api/books/search").param("q", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void searchBooksMissingQueryReturns400() throws Exception {
        mockMvc.perform(get("/api/books/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBooksDoesNotReturnInactiveBooks() throws Exception {
        mockMvc.perform(get("/api/books/search").param("q", "CatTestBook Inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + inactiveBook + ")]").isEmpty());
    }

    @Test
    void searchBooksMinPriceAboveMaxPriceReturns400() throws Exception {
        mockMvc.perform(get("/api/books/search")
                        .param("q", "CatTestBook")
                        .param("minPrice", "1000")
                        .param("maxPrice", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ GET /api/books/{id}

    @Test
    void getBookByIdReturnsDetailForActiveBook() throws Exception {
        mockMvc.perform(get("/api/books/" + bookA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookA))
                .andExpect(jsonPath("$.title").value("CatTestBook Alpha"))
                .andExpect(jsonPath("$.price").value(1200.00))
                .andExpect(jsonPath("$.isbn").isNotEmpty())
                .andExpect(jsonPath("$.description").isString())
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.maxOrderableQuantity").value(Matchers.greaterThan(0)));
    }

    @Test
    void getInactiveBookReturns404BookNotFound() throws Exception {
        mockMvc.perform(get("/api/books/" + inactiveBook))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void getNonExistentBookReturns404() throws Exception {
        mockMvc.perform(get("/api/books/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void nonNumericBookIdReturns400() throws Exception {
        mockMvc.perform(get("/api/books/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroBookIdReturns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/books/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ GET /api/books/{id}/related

    @Test
    void getRelatedBooksReturnsArrayForActiveBook() throws Exception {
        mockMvc.perform(get("/api/books/" + bookA + "/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getRelatedBooksNeverIncludesTheBookItself() throws Exception {
        mockMvc.perform(get("/api/books/" + bookA + "/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + bookA + ")]").isEmpty());
    }

    @Test
    void getRelatedBooksForInactiveBookReturns404() throws Exception {
        mockMvc.perform(get("/api/books/" + inactiveBook + "/related"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void relatedBooksLimitParameterRestrictsResults() throws Exception {
        mockMvc.perform(get("/api/books/" + bookA + "/related").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Matchers.lessThanOrEqualTo(2)));
    }

    @Test
    void invalidRelatedLimitReturns400() throws Exception {
        mockMvc.perform(get("/api/books/" + bookA + "/related").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/books/" + bookA + "/related").param("limit", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ GET /api/categories and /api/publishers

    @Test
    void listCategoriesReturnsNonEmptyArraySortedByName() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").isString());
    }

    @Test
    void listPublishersReturnsNonEmptyArray() throws Exception {
        mockMvc.perform(get("/api/publishers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").isString());
    }

    @Test
    void catalogueEndpointsArePublicAndDoNotRequireAuthentication() throws Exception {
        // Verify that no 401 is returned for any catalogue endpoint
        for (String path : new String[]{"/api/books", "/api/books/search?q=test", "/api/books/" + bookA,
                "/api/books/" + bookA + "/related", "/api/categories", "/api/publishers"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(status).as(path).isNotIn(401, 403);
        }
    }

    // ------------------------------------------------------------------ helpers

    private long insertBook(String title, String price, int stock, boolean active) {
        long isbn = ISBN_SEQUENCE.incrementAndGet();
        long id = jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, active, category_id, publisher_id)
                VALUES (?, ?, 'catalogue test book', ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, title, String.valueOf(isbn), new BigDecimal(price), stock, active,
                categoryId, publisherId);
        jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", id, authorId);
        return id;
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
        }
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }
}
