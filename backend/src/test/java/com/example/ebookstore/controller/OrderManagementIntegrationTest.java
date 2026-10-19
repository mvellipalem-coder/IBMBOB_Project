package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import com.example.ebookstore.service.payment.SimulatedPaymentGateway;
import com.jayway.jsonpath.JsonPath;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Integration tests for Order Management covering order history, order details,
 * cancellation constraints, refunds, buy-again scenarios, and ownership isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderManagementIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9784000000000L);

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
        registry.add("logging.level.org.springframework.boot.autoconfigure", () -> "WARN");
        registry.add("logging.level.org.springframework", () -> "INFO");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SimulatedPaymentGateway simulatedPaymentGateway;

    private TestUser alice;
    private TestUser bob;
    private long aliceAddress;
    private long book1;
    private long book2;

    @BeforeEach
    void setUp() throws Exception {
        simulatedPaymentGateway.reset();
        alice = registerAndLogin();
        bob = registerAndLogin();
        aliceAddress = createAddress(alice);
        long category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        long publisher = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        book1 = insertBook("Domain-Driven Design", "850.00", 15, category, publisher);
        book2 = insertBook("Refactoring", "650.00", 10, category, publisher);
    }

    @AfterEach
    void tearDown() {
        simulatedPaymentGateway.reset();
    }

    @Test
    @DisplayName("Retrieve order history with pagination")
    void getOrderHistory_returnsPaginatedList() throws Exception {
        addToCart(alice, book1, 1);
        createOrder(alice, aliceAddress, "CARD", null);

        addToCart(alice, book2, 1);
        createOrder(alice, aliceAddress, "UPI", null);

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
    }

    @Test
    @DisplayName("View details of a specific order including items, address, delivery estimate and payment")
    void getOrderDetail_returnsFullDetails() throws Exception {
        addToCart(alice, book1, 2);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        // Process payment to confirm order
        mockMvc.perform(post("/api/payments/" + orderId + "/process")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .header("Idempotency-Key", key()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.orderNumber").value(Matchers.matchesPattern("EB-\\d{8}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.subtotal").value(1700.00))
                .andExpect(jsonPath("$.totalAmount").value(1700.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].bookId").value(book1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(850.00))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Asha Rao"))
                .andExpect(jsonPath("$.expectedDeliveryDate").isNotEmpty())
                .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.transactionRef").isNotEmpty())
                .andExpect(jsonPath("$.cancellation.cancellable").value(true))
                .andExpect(jsonPath("$.statusHistory").isArray());
    }

    @Test
    @DisplayName("Order ownership isolation: User cannot view or cancel another user's order")
    void ownershipViolation_returns404() throws Exception {
        addToCart(alice, book1, 1);
        long aliceOrderId = createOrder(alice, aliceAddress, "CARD", null);

        // Bob tries to view Alice's order
        mockMvc.perform(get("/api/orders/" + aliceOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // Bob tries to cancel Alice's order
        mockMvc.perform(post("/api/orders/" + aliceOrderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Malicious cancel\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Cancellation within 48h restores stock, refunds gift points and issues mock payment refund")
    void cancelOrder_within48Hours_succeedsAndRestoresStockAndPointsAndRefundsPayment() throws Exception {
        grantPoints(alice, 200);
        addToCart(alice, book1, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", 50);

        // Pay for order
        mockMvc.perform(post("/api/payments/" + orderId + "/process")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .header("Idempotency-Key", key()))
                .andExpect(status().isOk());

        assertThat(stockOf(book1)).isEqualTo(14);
        assertThat(balanceInDatabase(alice)).isEqualTo(150);

        // Cancel order
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Found cheaper elsewhere\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Found cheaper elsewhere"))
                .andExpect(jsonPath("$.cancellation.cancellable").value(false))
                .andExpect(jsonPath("$.cancellation.reason").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.refundedAt").isNotEmpty());

        // Stock restored
        assertThat(stockOf(book1)).isEqualTo(15);
        // Gift points refunded
        assertThat(balanceInDatabase(alice)).isEqualTo(200);

        // Check payment in DB
        String paymentStatus = jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        assertThat(paymentStatus).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("Cancellation after 48 hours is refused with 409 CANCELLATION_WINDOW_EXPIRED")
    void cancelOrder_after48Hours_failsWith409() throws Exception {
        addToCart(alice, book1, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        // Fast-forward order creation date in DB to 49 hours ago
        jdbc.update("UPDATE orders SET created_at = NOW() - INTERVAL '49 hours' WHERE id = ?", orderId);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_WINDOW_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Orders can only be cancelled within 48 hours of being placed."));
    }

    @Test
    @DisplayName("Cancellation of already shipped or delivered orders is refused with 409 ORDER_NOT_CANCELLABLE_STATUS")
    void cancelOrder_ineligibleStatus_failsWith409() throws Exception {
        addToCart(alice, book1, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        jdbc.update("UPDATE orders SET status = 'SHIPPED' WHERE id = ?", orderId);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE_STATUS"))
                .andExpect(jsonPath("$.message").value("Orders that are SHIPPED or DELIVERED cannot be cancelled."));
    }

    @Test
    @DisplayName("Duplicate cancellation is refused with 409 ORDER_ALREADY_CANCELLED")
    void cancelOrder_duplicateCancel_failsWith409() throws Exception {
        addToCart(alice, book1, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Second cancel attempt
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.message").value("This order is already cancelled."));
    }

    @Test
    @DisplayName("Buy-again adds previously purchased books to active cart")
    void buyAgain_addsItemsToCartSuccessfully() throws Exception {
        addToCart(alice, book1, 2);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        // Buy again
        mockMvc.perform(post("/api/orders/" + orderId + "/buy-again")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.items.length()").value(1))
                .andExpect(jsonPath("$.cart.items[0].book.id").value(book1))
                .andExpect(jsonPath("$.cart.items[0].quantity").value(2))
                .andExpect(jsonPath("$.issues").isEmpty());
    }

    @Test
    @DisplayName("Buy-again handles out of stock, inactive books and returns issues list")
    void buyAgain_withUnavailableBooks_returnsIssuesOr409() throws Exception {
        addToCart(alice, book1, 2);
        addToCart(alice, book2, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        // Make book1 out of stock and book2 inactive
        jdbc.update("UPDATE books SET stock_quantity = 0 WHERE id = ?", book1);
        jdbc.update("UPDATE books SET active = false WHERE id = ?", book2);

        // All items unavailable -> 409 NO_ITEMS_AVAILABLE
        mockMvc.perform(post("/api/orders/" + orderId + "/buy-again")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ITEMS_AVAILABLE"));

        // Restore book1 with limited stock (1 copy when order requested 2)
        jdbc.update("UPDATE books SET stock_quantity = 1 WHERE id = ?", book1);

        mockMvc.perform(post("/api/orders/" + orderId + "/buy-again")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.items.length()").value(1))
                .andExpect(jsonPath("$.cart.items[0].book.id").value(book1))
                .andExpect(jsonPath("$.cart.items[0].quantity").value(1))
                .andExpect(jsonPath("$.issues.length()").value(2))
                .andExpect(jsonPath("$.issues[0].reason").value("QUANTITY_REDUCED"))
                .andExpect(jsonPath("$.issues[1].reason").value("BOOK_INACTIVE"));
    }

    // ------------------------------------------------------------------ helpers

    private record TestUser(String token, long id) {
    }

    private long createOrder(TestUser user, long addressId, String method, Integer points) throws Exception {
        String body = "{\"addressId\":" + addressId + ",\"paymentMethod\":\"" + method + "\""
                + (points == null ? "" : ",\"pointsToRedeem\":" + points) + "}";
        MvcResult result = mockMvc.perform(post("/api/checkout")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private void addToCart(TestUser user, long bookId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().is2xxSuccessful());
    }

    private long createAddress(TestUser user) throws Exception {
        String body = """
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
                """;
        MvcResult result = mockMvc.perform(post("/api/addresses")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private TestUser registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String reg = "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isCreated());

        String login = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new TestUser(token, id);
    }

    private long insertBook(String title, String price, int stock, long categoryId, long publisherId) {
        long isbn = ISBN_SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, category_id, publisher_id)
                VALUES (?, ?, 'order test book', ?, ?, ?, ?) RETURNING id
                """, Long.class, title, String.valueOf(isbn), new BigDecimal(price), stock, categoryId, publisherId);
    }

    private int stockOf(long bookId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM books WHERE id = ?", Integer.class, bookId);
    }

    private int balanceInDatabase(TestUser user) {
        return jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Integer.class, user.id());
    }

    private void grantPoints(TestUser user, int points) {
        jdbc.update("""
                INSERT INTO gift_point_transactions (user_id, order_id, type, points, balance_after, description)
                VALUES (?, NULL, 'ADJUST', ?, 0, 'test grant')
                """, user.id(), points);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new RuntimeException("EmbeddedPostgres failed to start", e);
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            try {
                POSTGRES.close();
            } catch (IOException ignored) {
            }
        }
    }
}
