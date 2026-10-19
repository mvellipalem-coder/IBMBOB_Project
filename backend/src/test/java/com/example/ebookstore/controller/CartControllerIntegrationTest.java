package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * End-to-end test of the cart API: the real application, real Spring Security with real JWTs, and a real
 * (embedded) PostgreSQL with the real Flyway schema. Each test registers fresh users and inserts its own books, so
 * tests do not depend on each other or on the seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9781000000000L);

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

    private String tokenA;
    private String tokenB;
    private long cleanCode;
    private long pragmatic;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerAndLogin();
        tokenB = registerAndLogin();
        long category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        long publisher = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        cleanCode = insertBook("Cart Test Clean Code", "899.00", 2, category, publisher);
        pragmatic = insertBook("Cart Test Pragmatic Programmer", "949.00", 50, category, publisher);
    }

    // ------------------------------------------------------------------ the main journey

    @Test
    void addTwoBooksIncreaseAQuantityRemoveAnItemAndReadTheCart() throws Exception {
        // 1. A new cart is empty (never 404).
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalQuantity").value(0))
            .andExpect(jsonPath("$.subtotal").value(0.0));

        // 2. First book: a new line -> 201, with server-calculated prices.
        MvcResult first = mockMvc.perform(add(tokenA, "{\"bookId\":" + cleanCode + ",\"quantity\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].book.title").value("Cart Test Clean Code"))
            .andExpect(jsonPath("$.items[0].quantity").value(1))
            .andExpect(jsonPath("$.items[0].lineTotal").value(899.00))
            .andExpect(jsonPath("$.items[0].maxQuantity").value(2))
            .andExpect(jsonPath("$.totalQuantity").value(1))
            .andExpect(jsonPath("$.subtotal").value(899.00))
            .andReturn();
        long cleanCodeItem = itemId(first, 0);

        // 3. Second, different book: another new line -> 201.
        MvcResult second = mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalQuantity").value(2))
            .andExpect(jsonPath("$.subtotal").value(1848.00))
            .andReturn();
        long pragmaticItem = itemId(second, 1);

        // 4. Adding the same book again adds quantities (one line per book) -> 200.
        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[1].quantity").value(3))
            .andExpect(jsonPath("$.items[1].lineTotal").value(2847.00))
            .andExpect(jsonPath("$.subtotal").value(3746.00));

        // 5. Increasing a quantity through PUT sets (not adds) it.
        mockMvc.perform(update(tokenA, pragmaticItem, "{\"quantity\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[1].quantity").value(5))
            .andExpect(jsonPath("$.items[1].lineTotal").value(4745.00))
            .andExpect(jsonPath("$.totalQuantity").value(6))
            .andExpect(jsonPath("$.subtotal").value(5644.00));

        // 6. Removing an item returns the updated cart.
        mockMvc.perform(delete("/api/cart/items/" + cleanCodeItem).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].book.title").value("Cart Test Pragmatic Programmer"))
            .andExpect(jsonPath("$.subtotal").value(4745.00));

        // 7. Retrieving the cart shows the same final state.
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].itemId").value(pragmaticItem))
            .andExpect(jsonPath("$.totalQuantity").value(5))
            .andExpect(jsonPath("$.subtotal").value(4745.00));
    }

    // ------------------------------------------------------------------ stock and quantity validation

    @Test
    void insufficientStockIsRejectedWith409AndAClearMessageOnAddAndOnUpdate() throws Exception {
        mockMvc.perform(add(tokenA, "{\"bookId\":" + cleanCode + ",\"quantity\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Only 2 copies of \"Cart Test Clean Code\" are available."))
            .andExpect(jsonPath("$.path").value("/api/cart/items"));
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$.items").isEmpty());

        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + cleanCode + ",\"quantity\":2}"))
            .andExpect(status().isCreated()).andReturn();
        long item = itemId(added, 0);

        mockMvc.perform(add(tokenA, "{\"bookId\":" + cleanCode + ",\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(update(tokenA, item, "{\"quantity\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(quantityInDatabase(item)).isEqualTo(2);
    }

    @Test
    void morePerLineThanTheLimitOfTenIsRejected() throws Exception {
        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":8}"))
            .andExpect(status().isCreated()).andReturn();

        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CART_LINE_LIMIT_EXCEEDED"));
        assertThat(quantityInDatabase(itemId(added, 0))).isEqualTo(8);
    }

    @Test
    void zeroNegativeTooLargeAndMissingQuantitiesAreRejectedWith400AndFieldErrors() throws Exception {
        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":2}"))
            .andExpect(status().isCreated()).andReturn();
        long item = itemId(added, 0);

        for (String quantity : new String[] {"0", "-1", "11"}) {
            mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be between 1 and 10"));
            mockMvc.perform(update(tokenA, item, "{\"quantity\":" + quantity + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        }
        mockMvc.perform(update(tokenA, item, "{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        mockMvc.perform(add(tokenA, "{\"quantity\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("bookId"));
        mockMvc.perform(add(tokenA, "{\"bookId\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("bookId"));
        assertThat(quantityInDatabase(item)).isEqualTo(2);
    }

    @Test
    void pricesTotalsAndOtherUnknownFieldsFromTheClientAreRejectedNotTrusted() throws Exception {
        for (String body : new String[] {
                "{\"bookId\":" + pragmatic + ",\"price\":1.00}",
                "{\"bookId\":" + pragmatic + ",\"lineTotal\":1.00}",
                "{\"bookId\":" + pragmatic + ",\"userId\":1}"}) {
            mockMvc.perform(add(tokenA, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + "}"))
            .andExpect(status().isCreated()).andReturn();
        mockMvc.perform(update(tokenA, itemId(added, 0), "{\"quantity\":2,\"subtotal\":1.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$.subtotal").value(949.00));
    }

    @Test
    void anUnknownOrInactiveBookCannotBeAdded() throws Exception {
        mockMvc.perform(add(tokenA, "{\"bookId\":999999999}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));

        jdbc.update("UPDATE books SET active = FALSE WHERE id = ?", pragmatic);
        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + "}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ books that become unavailable

    @Test
    void aBookThatBecomesUnavailableStaysInTheCartIsFlaggedCannotBeIncreasedButCanBeRemoved() throws Exception {
        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + cleanCode + ",\"quantity\":2}"))
            .andExpect(status().isCreated()).andReturn();
        long item = itemId(added, 0);

        // Stock drops below what is in the cart.
        jdbc.update("UPDATE books SET stock_quantity = 1 WHERE id = ?", cleanCode);
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].maxQuantity").value(1))
            .andExpect(jsonPath("$.items[0].book.inStock").value(true));

        // The book is withdrawn from sale.
        jdbc.update("UPDATE books SET active = FALSE WHERE id = ?", cleanCode);
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].maxQuantity").value(0))
            .andExpect(jsonPath("$.items[0].book.inStock").value(false));
        mockMvc.perform(update(tokenA, item, "{\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.message").value(
                    "\"Cart Test Clean Code\" is no longer available. Remove it from your cart."));

        mockMvc.perform(delete("/api/cart/items/" + item).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void theCartUsesTheCurrentCataloguePriceNotThePriceAtTheTimeOfAdding() throws Exception {
        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":2}")).andExpect(status().isCreated());

        jdbc.update("UPDATE books SET price = ? WHERE id = ?", new BigDecimal("1000.00"), pragmatic);

        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$.items[0].book.price").value(1000.00))
            .andExpect(jsonPath("$.items[0].lineTotal").value(2000.00))
            .andExpect(jsonPath("$.subtotal").value(2000.00));
    }

    // ------------------------------------------------------------------ one cart per user

    @Test
    void eachUserHasAnIndependentCart() throws Exception {
        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":2}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(add(tokenB, "{\"bookId\":" + cleanCode + "}")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].book.title").value("Cart Test Clean Code"));
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].book.title").value("Cart Test Pragmatic Programmer"));
    }

    @Test
    void oneUserCannotReadChangeOrRemoveAnotherUsersCartLine() throws Exception {
        MvcResult added = mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + ",\"quantity\":2}"))
            .andExpect(status().isCreated()).andReturn();
        long itemOfA = itemId(added, 0);
        // B has a cart of their own, so the lookup really is "wrong owner", not "no cart".
        mockMvc.perform(add(tokenB, "{\"bookId\":" + cleanCode + "}")).andExpect(status().isCreated());

        mockMvc.perform(update(tokenB, itemOfA, "{\"quantity\":9}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        mockMvc.perform(delete("/api/cart/items/" + itemOfA).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        // A user who has never had a cart gets the same answer.
        String tokenC = registerAndLoginSafely();
        mockMvc.perform(delete("/api/cart/items/" + itemOfA).header(HttpHeaders.AUTHORIZATION, bearer(tokenC)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        assertThat(quantityInDatabase(itemOfA)).isEqualTo(2);
    }

    @Test
    void aUserIdSentByTheClientIsIgnored() throws Exception {
        mockMvc.perform(add(tokenA, "{\"bookId\":" + pragmatic + "}")).andExpect(status().isCreated());
        long idA = jdbc.queryForObject("SELECT user_id FROM carts ORDER BY id DESC LIMIT 1", Long.class);

        mockMvc.perform(get("/api/cart").param("userId", String.valueOf(idA)).header("X-User-Id", String.valueOf(idA))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void everyCartEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":1}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/cart/items/1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/cart/items/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void aNonNumericOrNonPositiveItemIdIsAValidationError() throws Exception {
        mockMvc.perform(delete("/api/cart/items/abc").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(delete("/api/cart/items/0").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ helpers

    private static MockHttpServletRequestBuilder add(String token, String body) {
        return post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder update(String token, long itemId, String body) {
        return put("/api/cart/items/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static long itemId(MvcResult result, int index) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.items[" + index + "].itemId");
        return id.longValue();
    }

    private int quantityInDatabase(long itemId) {
        return jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id = ?", Integer.class, itemId);
    }

    private String registerAndLoginSafely() throws Exception {
        return registerAndLogin();
    }

    private String registerAndLogin() throws Exception {
        String email = "cart-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private long insertBook(String title, String price, int stock, long categoryId, long publisherId) {
        return jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, category_id, publisher_id)
                VALUES (?, ?, 'cart test', ?, ?, ?, ?) RETURNING id
                """, Long.class, title, String.valueOf(ISBN_SEQUENCE.incrementAndGet()), new BigDecimal(price), stock,
                categoryId, publisherId);
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
