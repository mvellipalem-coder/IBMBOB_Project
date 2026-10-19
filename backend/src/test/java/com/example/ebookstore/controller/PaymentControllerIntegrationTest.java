package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.example.ebookstore.service.payment.SimulatedPaymentGateway;
import com.jayway.jsonpath.JsonPath;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * End-to-end integration tests for payment processing, purchase confirmation, and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9783000000000L);

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
    private long designing;
    private long effective;

    @BeforeEach
    void setUp() throws Exception {
        simulatedPaymentGateway.reset();
        alice = registerAndLogin();
        bob = registerAndLogin();
        aliceAddress = createAddress(alice);
        long category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        long publisher = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        designing = insertBook("Designing Data-Intensive Applications", "1199.00", 10, category, publisher);
        effective = insertBook("Effective Java", "500.00", 10, category, publisher);
    }

    @AfterEach
    void tearDown() {
        simulatedPaymentGateway.reset();
    }

    @Test
    void successfulPaymentUpdatesPaymentStatusSetsTransactionRefAndPaidAt() throws Exception {
        addToCart(alice, designing, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        String paymentKey = key();
        MvcResult result = mockMvc.perform(processPayment(alice, orderId, paymentKey))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(1199.00))
                .andExpect(jsonPath("$.transactionRef").value(Matchers.startsWith("SIM-")))
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andReturn();

        String transactionRef = JsonPath.read(result.getResponse().getContentAsString(), "$.transactionRef");
        assertThat(transactionRef).matches("^SIM-[0-9A-F]{12}$");

        // Verify order confirmation / purchase summary via GET /api/orders/{id}
        mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.orderNumber").value(Matchers.matchesPattern("EB-\\d{8}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.totalAmount").value(1199.00))
                .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.transactionRef").value(transactionRef))
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Designing Data-Intensive Applications"))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Asha Rao"))
                .andExpect(jsonPath("$.expectedDeliveryDate").isNotEmpty());

        // Stock remains decremented
        assertThat(stockOf(designing)).isEqualTo(9);
    }

    @Test
    void failedPaymentCancelsOrderRestoresStockRefundsGiftPointsAndRecordsFailureReason() throws Exception {
        grantPoints(alice, 300);
        addToCart(alice, designing, 1);
        long orderId = createOrder(alice, aliceAddress, "UPI", 100);

        assertThat(stockOf(designing)).isEqualTo(9);
        assertThat(balanceInDatabase(alice)).isEqualTo(200);

        // Instruct gateway to simulate failure
        simulatedPaymentGateway.setSimulateFailure(true);
        simulatedPaymentGateway.setFailureReason("Insufficient funds in mock bank account");

        mockMvc.perform(processPayment(alice, orderId, key()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient funds in mock bank account"))
                .andExpect(jsonPath("$.paidAt").doesNotExist())
                .andExpect(jsonPath("$.transactionRef").doesNotExist());

        // Order is automatically cancelled
        String orderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("CANCELLED");

        // Stock was restored
        assertThat(stockOf(designing)).isEqualTo(10);

        // Redeemed points were refunded
        assertThat(balanceInDatabase(alice)).isEqualTo(300);
        long refundCount = jdbc.queryForObject(
                "SELECT count(*) FROM gift_point_transactions WHERE order_id = ? AND type = 'REFUND'",
                Long.class, orderId);
        assertThat(refundCount).isEqualTo(1);
    }

    @Test
    void duplicatePaymentRequestWithSameKeyReturnsReplayedResultAndDoesNotChargeAgain() throws Exception {
        addToCart(alice, effective, 2);
        long orderId = createOrder(alice, aliceAddress, "UPI", null);

        String paymentKey = key();
        MvcResult first = mockMvc.perform(processPayment(alice, orderId, paymentKey))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        MvcResult retry = mockMvc.perform(processPayment(alice, orderId, paymentKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        String ref1 = JsonPath.read(first.getResponse().getContentAsString(), "$.transactionRef");
        String ref2 = JsonPath.read(retry.getResponse().getContentAsString(), "$.transactionRef");
        assertThat(ref1).isEqualTo(ref2);
    }

    @Test
    void differentKeyOnAlreadyPaidOrderReturns409PaymentAlreadySettled() throws Exception {
        addToCart(alice, effective, 1);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        mockMvc.perform(processPayment(alice, orderId, key()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // New key on already settled payment
        mockMvc.perform(processPayment(alice, orderId, key()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_SETTLED"))
                .andExpect(jsonPath("$.message").value("This payment has already been processed."));
    }

    @Test
    void invalidOrderOwnershipRejectedWith404() throws Exception {
        addToCart(alice, designing, 1);
        long aliceOrderId = createOrder(alice, aliceAddress, "CARD", null);

        // Bob tries to pay for Alice's order
        mockMvc.perform(processPayment(bob, aliceOrderId, key()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        // Bob tries to get Alice's payment
        mockMvc.perform(get("/api/payments/" + aliceOrderId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // Non-existent order
        mockMvc.perform(processPayment(alice, 999_999_999L, key()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cashOnDeliveryOrderReturns409PaymentNotRequired() throws Exception {
        addToCart(alice, designing, 1);
        long orderId = createOrder(alice, aliceAddress, "COD", null);

        mockMvc.perform(processPayment(alice, orderId, key()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Cash on delivery orders are paid on delivery."));
    }

    @Test
    void cancelledOrderReturns409OrderNotPayable() throws Exception {
        addToCart(alice, designing, 1);
        long orderId = createOrder(alice, aliceAddress, "UPI", null);

        // Manually cancel order first
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Try paying for cancelled order
        mockMvc.perform(processPayment(alice, orderId, key()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"))
                .andExpect(jsonPath("$.message").value("A cancelled order cannot be paid."));
    }

    @Test
    void idempotencyKeyReusedForDifferentOrderReturns422() throws Exception {
        addToCart(alice, designing, 1);
        long order1 = createOrder(alice, aliceAddress, "CARD", null);

        addToCart(alice, effective, 1);
        long order2 = createOrder(alice, aliceAddress, "CARD", null);

        String sharedKey = key();
        mockMvc.perform(processPayment(alice, order1, sharedKey))
                .andExpect(status().isOk());

        mockMvc.perform(processPayment(alice, order2, sharedKey))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void unauthenticatedPaymentRequestReturns401() throws Exception {
        mockMvc.perform(post("/api/payments/1/process").header("Idempotency-Key", key()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getPaymentReturnsExistingPaymentDetails() throws Exception {
        addToCart(alice, designing, 1);
        long orderId = createOrder(alice, aliceAddress, "UPI", null);

        mockMvc.perform(get("/api/payments/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(1199.00));
    }

    @Test
    void listOrdersReturnsPaginatedSummaries() throws Exception {
        addToCart(alice, designing, 1);
        createOrder(alice, aliceAddress, "CARD", null);

        addToCart(alice, effective, 1);
        createOrder(alice, aliceAddress, "UPI", null);

        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
    }

    @Test
    void buyAgainAddsOrderItemsBackToCart() throws Exception {
        addToCart(alice, designing, 2);
        long orderId = createOrder(alice, aliceAddress, "CARD", null);

        mockMvc.perform(post("/api/orders/" + orderId + "/buy-again")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.items.length()").value(1))
                .andExpect(jsonPath("$.cart.items[0].book.id").value(designing))
                .andExpect(jsonPath("$.cart.items[0].quantity").value(2))
                .andExpect(jsonPath("$.issues").isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private record TestUser(String token, long id) {
    }

    private static MockHttpServletRequestBuilder processPayment(TestUser user, long orderId, String idempotencyKey) {
        return post("/api/payments/" + orderId + "/process")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", idempotencyKey);
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
        MvcResult result = mockMvc.perform(post("/api/addresses")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientName":"Asha Rao","phone":"+91 98765 43210","line1":"12 MG Road","line2":"Flat 4B",
                         "city":"Pune","state":"Maharashtra","postalCode":"411001"}
                        """))
                .andExpect(status().isCreated()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private void grantPoints(TestUser user, int points) {
        jdbc.update("""
                INSERT INTO gift_point_transactions (user_id, order_id, type, points, balance_after, description)
                VALUES (?, NULL, 'ADJUST', ?, 0, 'test grant')
                """, user.id(), points);
    }

    private int balanceInDatabase(TestUser user) {
        return jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Integer.class, user.id());
    }

    private int stockOf(long bookId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM books WHERE id = ?", Integer.class, bookId);
    }

    private TestUser registerAndLogin() throws Exception {
        String email = "paytest-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new TestUser(token, id);
    }

    private long insertBook(String title, String price, int stock, long categoryId, long publisherId) {
        return jdbc.queryForObject("""
                INSERT INTO books (title, isbn, description, price, stock_quantity, category_id, publisher_id)
                VALUES (?, ?, 'payment test', ?, ?, ?, ?) RETURNING id
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
