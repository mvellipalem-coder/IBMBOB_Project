package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * End-to-end integration tests for the /api/recommendations endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9785000000000L);

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

    private TestUser alice;
    private long categoryTech;
    private long categoryFiction;
    private long publisherA;
    private long publisherB;
    private long authorMartin;
    private long authorBloch;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin();

        categoryTech = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        categoryFiction = insertCategory("Fiction Test");
        publisherA = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        publisherB = insertPublisher("Publisher B Test");
        authorMartin = insertAuthor("Robert C. Martin");
        authorBloch = insertAuthor("Joshua Bloch");
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void newUserWithoutOrdersGetsPopularStrategyWithActiveInStockBooks() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("POPULAR"))
                .andExpect(jsonPath("$.books").isArray())
                .andExpect(jsonPath("$.books.length()").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.books[0].id").isNumber())
                .andExpect(jsonPath("$.books[0].title").isNotEmpty())
                .andExpect(jsonPath("$.books[0].authors").isArray())
                .andExpect(jsonPath("$.books[0].price").isNumber())
                .andExpect(jsonPath("$.books[0].categoryId").isNumber())
                .andExpect(jsonPath("$.books[0].publisherId").isNumber());
    }

    @Test
    void returningUserGetsPurchaseHistoryStrategyExcludingBoughtBooks() throws Exception {
        // Create books in categoryTech and categoryFiction
        long boughtBook = insertBookWithAuthor("Clean Code", "800.00", 10, categoryTech, publisherA, authorMartin);
        long relatedBook1 = insertBookWithAuthor("Clean Architecture", "850.00", 10, categoryTech, publisherA, authorMartin);
        long relatedBook2 = insertBookWithAuthor("Effective Java", "900.00", 10, categoryTech, publisherB, authorBloch);

        // Alice buys boughtBook
        long addressId = createAddress(alice);
        addToCart(alice, boughtBook, 1);
        createOrder(alice, addressId, "CARD");

        // Alice requests recommendations
        MvcResult result = mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("PURCHASE_HISTORY"))
                .andExpect(jsonPath("$.books").isArray())
                .andReturn();

        List<Integer> bookIds = JsonPath.read(result.getResponse().getContentAsString(), "$.books[*].id");
        assertThat(bookIds).contains((int) relatedBook1, (int) relatedBook2);
        // Must exclude already bought book
        assertThat(bookIds).doesNotContain((int) boughtBook);
    }

    @Test
    void cancelledOrdersAreIgnoredAndFallBackToPopular() throws Exception {
        long boughtBook = insertBookWithAuthor("Temporary Book", "500.00", 10, categoryFiction, publisherB, authorBloch);
        long addressId = createAddress(alice);
        addToCart(alice, boughtBook, 1);
        long orderId = createOrder(alice, addressId, "CARD");

        // Cancel order
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Now recommendations should fallback to POPULAR since non-cancelled orders are 0
        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("POPULAR"));
    }

    @Test
    void limitParameterRestrictsNumberOfRecommendations() throws Exception {
        mockMvc.perform(get("/api/recommendations?limit=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(Matchers.lessThanOrEqualTo(2)));
    }

    @Test
    void invalidLimitParameterReturns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/recommendations?limit=0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/recommendations?limit=25")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void outOfStockAndInactiveBooksAreNotRecommended() throws Exception {
        long outOfStock = insertBookWithAuthor("Out of Stock Book", "400.00", 0, categoryTech, publisherA, authorMartin);
        long inactive = insertBookWithAuthor("Inactive Book", "450.00", 10, categoryTech, publisherA, authorMartin);
        jdbc.update("UPDATE books SET active = false WHERE id = ?", inactive);

        long boughtBook = insertBookWithAuthor("Trigger Book", "500.00", 10, categoryTech, publisherA, authorBloch);
        long addressId = createAddress(alice);
        addToCart(alice, boughtBook, 1);
        createOrder(alice, addressId, "CARD");

        MvcResult result = mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> bookIds = JsonPath.read(result.getResponse().getContentAsString(), "$.books[*].id");
        assertThat(bookIds).doesNotContain((int) outOfStock, (int) inactive);
    }

    // ------------------------------------------------------------------ helpers

    private TestUser registerAndLogin() throws Exception {
        String email = "rec-user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Test Customer",
                                  "email": "%s",
                                  "password": "%s",
                                  "phone": "+91 98765 43210"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new TestUser(userId, email, token);
    }

    private long createAddress(TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Asha Rao",
                                  "phone": "+91 98765 43210",
                                  "line1": "12 MG Road",
                                  "line2": "Flat 4B",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "postalCode": "560001",
                                  "countryCode": "IN",
                                  "isDefault": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void addToCart(TestUser user, long bookId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookId": %d,
                                  "quantity": %d
                                }
                                """.formatted(bookId, quantity)))
                .andExpect(status().is2xxSuccessful());
    }

    private long createOrder(TestUser user, long addressId, String paymentMethod) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": %d,
                                  "paymentMethod": "%s"
                                }
                                """.formatted(addressId, paymentMethod)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long insertCategory(String name) {
        return jdbc.queryForObject("""
                INSERT INTO categories (name, description) VALUES (?, 'desc')
                ON CONFLICT (lower(name)) DO UPDATE SET name = EXCLUDED.name RETURNING id
                """, Long.class, name);
    }

    private long insertPublisher(String name) {
        return jdbc.queryForObject("""
                INSERT INTO publishers (name, description) VALUES (?, 'desc')
                ON CONFLICT (lower(name)) DO UPDATE SET name = EXCLUDED.name RETURNING id
                """, Long.class, name);
    }

    private long insertAuthor(String name) {
        return jdbc.queryForObject("""
                INSERT INTO authors (name) VALUES (?)
                ON CONFLICT (lower(name)) DO UPDATE SET name = EXCLUDED.name RETURNING id
                """, Long.class, name);
    }

    private long insertBookWithAuthor(String title, String price, int stock, long categoryId, long publisherId, long authorId) {
        String isbn = String.valueOf(ISBN_SEQUENCE.incrementAndGet());
        long bookId = jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, category_id, publisher_id)
                VALUES (?, ?, 'description', CAST(? AS numeric), ?, ?, ?) RETURNING id
                """, Long.class, title, isbn, price, stock, categoryId, publisherId);
        jdbc.update("INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)", bookId, authorId);
        return bookId;
    }

    private static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private static String key() {
        return "rec-test-" + UUID.randomUUID();
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start embedded postgres", e);
        }
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }

    private record TestUser(long id, String email, String token) {
    }
}
