package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
 * End-to-end test of checkout: the real application, real Spring Security with real JWTs, and a real (embedded)
 * PostgreSQL with the real Flyway schema, including the gift-point ledger trigger. Each test registers fresh users and
 * inserts its own books, so tests do not depend on each other or on the seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private static final AtomicLong ISBN_SEQUENCE = new AtomicLong(9782000000000L);

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
    private TestUser bob;
    private long aliceAddress;
    private long designing;   // 1199.00, stock 5
    private long effective;   // 500.00, stock 5

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin();
        bob = registerAndLogin();
        aliceAddress = createAddress(alice);
        long category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);
        long publisher = jdbc.queryForObject("SELECT id FROM publishers LIMIT 1", Long.class);
        designing = insertBook("Checkout Test Designing Data-Intensive Applications", "1199.00", 5, category, publisher);
        effective = insertBook("Checkout Test Effective Java", "500.00", 5, category, publisher);
    }

    // ------------------------------------------------------------------ the main journey

    @Test
    void checkoutCreatesAPlacedOrderWithServerCalculatedTotalsAndAPendingPayment() throws Exception {
        addToCart(alice, designing, 1);
        addToCart(alice, effective, 2);

        MvcResult result = mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", null)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.orderNumber").value(Matchers.matchesPattern("EB-\\d{8}-\\d{4,}")))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].title").value("Checkout Test Designing Data-Intensive Applications"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(1199.00))
            .andExpect(jsonPath("$.items[0].quantity").value(1))
            .andExpect(jsonPath("$.items[0].lineTotal").value(1199.00))
            .andExpect(jsonPath("$.items[1].quantity").value(2))
            .andExpect(jsonPath("$.items[1].lineTotal").value(1000.00))
            .andExpect(jsonPath("$.subtotal").value(2199.00))
            .andExpect(jsonPath("$.pointsRedeemed").value(0))
            .andExpect(jsonPath("$.pointsDiscount").value(0.00))
            .andExpect(jsonPath("$.totalAmount").value(2199.00))
            .andExpect(jsonPath("$.pointsEarned").value(219))
            .andExpect(jsonPath("$.shippingAddress.recipientName").value("Asha Rao"))
            .andExpect(jsonPath("$.shippingAddress.postalCode").value("411001"))
            .andExpect(jsonPath("$.payment.method").value("UPI"))
            .andExpect(jsonPath("$.payment.status").value("PENDING"))
            .andExpect(jsonPath("$.payment.amount").value(2199.00))
            .andExpect(jsonPath("$.payment.transactionRef").doesNotExist())
            .andExpect(jsonPath("$.statusHistory.length()").value(1))
            .andExpect(jsonPath("$.statusHistory[0].status").value("PLACED"))
            .andExpect(jsonPath("$.cancellation.cancellable").value(true))
            .andExpect(jsonPath("$.deliveredAt").doesNotExist())
            .andReturn();

        long orderId = number(result, "$.id");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/orders/" + orderId);

        // Expected delivery = order date + 5 days (no remote postal codes are configured by default).
        String json = result.getResponse().getContentAsString();
        LocalDate orderDate = OffsetDateTime.parse(JsonPath.<String>read(json, "$.createdAt")).toLocalDate();
        assertThat(JsonPath.<String>read(json, "$.expectedDeliveryDate")).isEqualTo(orderDate.plusDays(5).toString());

        // Stock was reduced, the cart was cleared, and the order and payment are stored.
        assertThat(stockOf(designing)).isEqualTo(4);
        assertThat(stockOf(effective)).isEqualTo(3);
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
            .andExpect(jsonPath("$.items").isEmpty());
        assertThat(orderCount(alice)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gift_point_transactions WHERE user_id = ?", Long.class,
                alice.id())).isZero();
    }

    @Test
    void theOrderKeepsACopyOfTheAddressAndThePriceEvenWhenTheyChangeLater() throws Exception {
        addToCart(alice, effective, 1);
        MvcResult result = mockMvc.perform(checkout(alice, key(), body(aliceAddress, "COD", null)))
            .andExpect(status().isCreated()).andReturn();
        long orderId = number(result, "$.id");

        jdbc.update("UPDATE addresses SET city = 'Changed City' WHERE id = ?", aliceAddress);
        jdbc.update("UPDATE books SET price = 1.00, title = 'Renamed' WHERE id = ?", effective);

        assertThat(jdbc.queryForObject("SELECT shipping_city FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("Pune");
        assertThat(jdbc.queryForObject("SELECT unit_price::text FROM order_items WHERE order_id = ?", String.class,
                orderId)).isEqualTo("500.00");
        assertThat(jdbc.queryForObject("SELECT book_title FROM order_items WHERE order_id = ?", String.class,
                orderId)).isEqualTo("Checkout Test Effective Java");
    }

    @Test
    void pricesAreRecalculatedFromTheCatalogueAtCheckout() throws Exception {
        addToCart(alice, effective, 2);
        jdbc.update("UPDATE books SET price = 700.00 WHERE id = ?", effective); // price changed after adding

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "CARD", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items[0].unitPrice").value(700.00))
            .andExpect(jsonPath("$.subtotal").value(1400.00))
            .andExpect(jsonPath("$.totalAmount").value(1400.00));
    }

    // ------------------------------------------------------------------ gift points

    @Test
    void redeemingPointsLowersTheTotalWritesALedgerEntryAndSpendsTheBalance() throws Exception {
        grantPoints(alice, 250);
        addToCart(alice, designing, 1);

        MvcResult result = mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 100)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(1199.00))
            .andExpect(jsonPath("$.pointsRedeemed").value(100))
            .andExpect(jsonPath("$.pointsDiscount").value(100.00))
            .andExpect(jsonPath("$.totalAmount").value(1099.00))
            .andExpect(jsonPath("$.pointsEarned").value(109))
            .andExpect(jsonPath("$.payment.amount").value(1099.00))
            .andReturn();
        long orderId = number(result, "$.id");

        assertThat(balanceInDatabase(alice)).isEqualTo(150);
        Map<String, Object> entry = jdbc.queryForMap(
                "SELECT type, points, balance_after, order_id FROM gift_point_transactions WHERE order_id = ?", orderId);
        assertThat(entry).containsEntry("type", "REDEEM").containsEntry("points", -100)
                .containsEntry("balance_after", 150);

        mockMvc.perform(get("/api/gift-points/balance").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(150))
            .andExpect(jsonPath("$.pointValue").value(1.00))
            .andExpect(jsonPath("$.maxRedeemPercentOfSubtotal").value(50))
            .andExpect(jsonPath("$.earnAmountPerPoint").value(10.00));
        mockMvc.perform(get("/api/gift-points/transactions").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].type").value("REDEEM"))
            .andExpect(jsonPath("$.content[0].points").value(-100))
            .andExpect(jsonPath("$.content[0].balanceAfter").value(150))
            .andExpect(jsonPath("$.content[0].orderId").value(orderId))
            .andExpect(jsonPath("$.content[1].type").value("ADJUST"));
        mockMvc.perform(get("/api/gift-points/transactions").param("type", "REDEEM")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void redeemingMoreThanTheBalanceIsRejectedAndNothingChanges() throws Exception {
        grantPoints(alice, 250);
        addToCart(alice, designing, 1);

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 300)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INVALID_POINTS_REDEMPTION"))
            .andExpect(jsonPath("$.message").value("You only have 250 gift points available."));

        assertNothingWasOrdered(alice, designing, 5, 250);
    }

    @Test
    void redeemingMoreThanHalfTheSubtotalIsRejectedWithTheMaximumInTheMessage() throws Exception {
        grantPoints(alice, 1000);
        addToCart(alice, designing, 1); // subtotal 1199 -> at most 599 points

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 600)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INVALID_POINTS_REDEMPTION"))
            .andExpect(jsonPath("$.message").value("You can redeem at most 599 points for this order."));
        assertNothingWasOrdered(alice, designing, 5, 1000);

        // Exactly the maximum is allowed.
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 599)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pointsDiscount").value(599.00))
            .andExpect(jsonPath("$.totalAmount").value(600.00));
        assertThat(balanceInDatabase(alice)).isEqualTo(401);
    }

    @Test
    void aUserWithoutPointsCannotRedeemAndNegativeOrHugeRequestsAreValidationErrors() throws Exception {
        addToCart(alice, designing, 1);

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 1)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INVALID_POINTS_REDEMPTION"))
            .andExpect(jsonPath("$.message").value("You only have 0 gift points available."));
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", -5)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("pointsToRedeem"));
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 1_000_001)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("pointsToRedeem"));
        mockMvc.perform(checkout(alice, key(), "{\"addressId\":" + aliceAddress
                + ",\"paymentMethod\":\"UPI\",\"pointsToRedeem\":\"many\"}"))
            .andExpect(status().isBadRequest());
        assertThat(balanceInDatabase(alice)).isZero();
        assertThat(orderCount(alice)).isZero();
    }

    @Test
    void theDatabaseNeverAllowsANegativeBalanceEvenIfTheServiceCheckWereBypassed() {
        grantPoints(alice, 10);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO gift_point_transactions (user_id, order_id, type, points, balance_after)
                VALUES (?, NULL, 'ADJUST', -11, 0)
                """, alice.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balanceInDatabase(alice)).isEqualTo(10);
    }

    // ------------------------------------------------------------------ addresses, empty cart, stock

    @Test
    void anAddressOfAnotherUserIsRejectedAsNotFound() throws Exception {
        long bobsAddress = createAddress(bob);
        addToCart(alice, effective, 1);

        mockMvc.perform(checkout(alice, key(), body(bobsAddress, "COD", null)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mockMvc.perform(checkout(alice, key(), body(999_999_999L, "COD", null)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        assertNothingWasOrdered(alice, effective, 5, 0);
    }

    @Test
    void anEmptyCartIsRejected() throws Exception {
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "COD", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMPTY_CART"))
            .andExpect(jsonPath("$.message").value("Your cart is empty."));
    }

    @Test
    void stockThatDroppedAfterAddingToTheCartBlocksCheckoutWithAClearMessage() throws Exception {
        addToCart(alice, effective, 3);
        jdbc.update("UPDATE books SET stock_quantity = 1 WHERE id = ?", effective);

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.message").value(
                    "Only 1 copy of \"Checkout Test Effective Java\" is available. Update your cart and try again."));

        jdbc.update("UPDATE books SET stock_quantity = 0 WHERE id = ?", effective);
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                    "\"Checkout Test Effective Java\" is out of stock. Update your cart and try again."));
        assertThat(cartLineCount(alice)).isEqualTo(1);
    }

    @Test
    void aBookWithdrawnFromSaleAfterAddingBlocksCheckoutAndNothingIsChanged() throws Exception {
        addToCart(alice, designing, 1);
        addToCart(alice, effective, 1);
        jdbc.update("UPDATE books SET active = FALSE WHERE id = ?", effective);

        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.message").value(
                    "\"Checkout Test Effective Java\" is no longer available. Update your cart and try again."));

        assertThat(stockOf(designing)).isEqualTo(5); // the first, valid line was not reserved either
        assertThat(cartLineCount(alice)).isEqualTo(2);
        assertThat(orderCount(alice)).isZero();
    }

    // ------------------------------------------------------------------ the client cannot influence money

    @Test
    void pricesTotalsStatusesAndUserIdsFromTheClientAreRejected() throws Exception {
        addToCart(alice, effective, 1);
        for (String extra : new String[] {"\"totalAmount\":1", "\"subtotal\":1", "\"price\":1", "\"pointsDiscount\":999",
                "\"status\":\"DELIVERED\"", "\"paymentStatus\":\"SUCCESS\"", "\"userId\":1", "\"items\":[]"}) {
            mockMvc.perform(checkout(alice, key(), "{\"addressId\":" + aliceAddress
                    + ",\"paymentMethod\":\"COD\"," + extra + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(orderCount(alice)).isZero();
    }

    @Test
    void missingOrInvalidFieldsAreValidationErrorsWithFieldNames() throws Exception {
        addToCart(alice, effective, 1);
        mockMvc.perform(checkout(alice, key(), "{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(checkout(alice, key(), "{\"addressId\":0,\"paymentMethod\":\"COD\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("addressId"));
        mockMvc.perform(checkout(alice, key(), "{\"addressId\":1,\"paymentMethod\":\"BITCOIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(checkout(alice, key(), "{\"addressId\":1,\"paymentMethod\":\"GIFT_POINTS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/checkout").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body(1, "COD", null)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void retryingWithTheSameKeyAndBodyReturnsTheSameOrderAndChangesNothingAgain() throws Exception {
        grantPoints(alice, 250);
        addToCart(alice, designing, 1);
        String key = key();
        String body = body(aliceAddress, "UPI", 100);

        MvcResult first = mockMvc.perform(checkout(alice, key, body)).andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed")).andReturn();
        // The cart is empty now, so a real second checkout would fail with EMPTY_CART; a replay must not.
        MvcResult retry = mockMvc.perform(checkout(alice, key, body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(header().string("Location", first.getResponse().getHeader("Location")))
            .andReturn();

        assertThat(number(retry, "$.id")).isEqualTo(number(first, "$.id"));
        assertThat(JsonPath.<String>read(retry.getResponse().getContentAsString(), "$.orderNumber"))
                .isEqualTo(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.orderNumber"));
        assertThat(JsonPath.<Object>read(retry.getResponse().getContentAsString(), "$.totalAmount"))
                .isEqualTo(JsonPath.<Object>read(first.getResponse().getContentAsString(), "$.totalAmount"));
        assertThat(orderCount(alice)).isEqualTo(1);
        assertThat(stockOf(designing)).isEqualTo(4);          // decremented once
        assertThat(balanceInDatabase(alice)).isEqualTo(150);  // points spent once
        assertThat(redeemEntries(alice)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejectedAs422() throws Exception {
        addToCart(alice, designing, 1);
        String key = key();
        mockMvc.perform(checkout(alice, key, body(aliceAddress, "UPI", null))).andExpect(status().isCreated());

        mockMvc.perform(checkout(alice, key, body(aliceAddress, "COD", null)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
            .andExpect(jsonPath("$.message").value("This Idempotency-Key was already used for a different request."));
        assertThat(orderCount(alice)).isEqualTo(1);
    }

    @Test
    void omittingPointsAndSendingZeroPointsAreTheSameRequest() throws Exception {
        addToCart(alice, designing, 1);
        String key = key();
        MvcResult first = mockMvc.perform(checkout(alice, key, body(aliceAddress, "UPI", null)))
            .andExpect(status().isCreated()).andReturn();
        mockMvc.perform(checkout(alice, key, body(aliceAddress, "UPI", 0)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(jsonPath("$.id").value(number(first, "$.id")));
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        long bobsAddress = createAddress(bob);
        addToCart(alice, effective, 1);
        addToCart(bob, effective, 1);
        String sharedKey = key();

        MvcResult a = mockMvc.perform(checkout(alice, sharedKey, body(aliceAddress, "COD", null)))
            .andExpect(status().isCreated()).andReturn();
        MvcResult b = mockMvc.perform(checkout(bob, sharedKey, body(bobsAddress, "COD", null)))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed")).andReturn();

        assertThat(number(a, "$.id")).isNotEqualTo(number(b, "$.id"));
    }

    @Test
    void aFailedCheckoutDoesNotBurnTheKeySoTheClientCanFixTheCartAndRetryWithTheSameKey() throws Exception {
        addToCart(alice, effective, 3);
        jdbc.update("UPDATE books SET stock_quantity = 1 WHERE id = ?", effective);
        String key = key();
        String body = body(aliceAddress, "UPI", null);

        mockMvc.perform(checkout(alice, key, body)).andExpect(status().isConflict());
        jdbc.update("UPDATE books SET stock_quantity = 10 WHERE id = ?", effective);

        mockMvc.perform(checkout(alice, key, body))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(orderCount(alice)).isEqualTo(1);
    }

    @Test
    void anExpiredKeyCanBeUsedAgain() throws Exception {
        addToCart(alice, effective, 1);
        String key = key();
        mockMvc.perform(checkout(alice, key, body(aliceAddress, "COD", null))).andExpect(status().isCreated());
        jdbc.update("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute' WHERE user_id = ?",
                alice.id());

        addToCart(alice, effective, 1);
        mockMvc.perform(checkout(alice, key, body(aliceAddress, "UPI", null)))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(orderCount(alice)).isEqualTo(2);
    }

    @Test
    void aMissingOrMalformedIdempotencyKeyIsAValidationError() throws Exception {
        addToCart(alice, effective, 1);
        mockMvc.perform(post("/api/checkout").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(body(aliceAddress, "COD", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));

        for (String bad : new String[] {"short", "has spaces in it 1234567", "x".repeat(65), "semi;colon-1234567890"}) {
            mockMvc.perform(checkout(alice, bad, body(aliceAddress, "COD", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        }
        assertThat(orderCount(alice)).isZero();
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void twoUsersBuyingTheLastCopyAtTheSameTimeNeverOversell() throws Exception {
        long bobsAddress = createAddress(bob);
        jdbc.update("UPDATE books SET stock_quantity = 1 WHERE id = ?", effective);
        addToCart(alice, effective, 1);
        addToCart(bob, effective, 1);

        List<Integer> statuses = runConcurrently(Arrays.asList(
                () -> checkoutStatus(alice, key(), body(aliceAddress, "COD", null)),
                () -> checkoutStatus(bob, key(), body(bobsAddress, "COD", null))));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(stockOf(effective)).isZero();
        assertThat(orderCount(alice) + orderCount(bob)).isEqualTo(1);
    }

    @Test
    void manyUsersRacingForLimitedStockSellExactlyTheStockThatExists() throws Exception {
        jdbc.update("UPDATE books SET stock_quantity = 4 WHERE id = ?", effective);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestUser buyer = registerAndLogin();
            long address = createAddress(buyer);
            addToCart(buyer, effective, 1);
            tasks.add(() -> checkoutStatus(buyer, key(), body(address, "COD", null)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(4);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(2);
        assertThat(stockOf(effective)).isZero();
    }

    @Test
    void theSameKeySentTwiceAtTheSameTimeCreatesExactlyOneOrder() throws Exception {
        grantPoints(alice, 250);
        addToCart(alice, designing, 1);
        String key = key();
        String body = body(aliceAddress, "UPI", 100);

        List<MvcResult> results = runConcurrently(Arrays.asList(
                () -> mockMvc.perform(checkout(alice, key, body)).andReturn(),
                () -> mockMvc.perform(checkout(alice, key, body)).andReturn()));

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactly(201, 201);
        assertThat(results.stream().filter(r -> "true".equals(r.getResponse().getHeader("Idempotent-Replayed")))
                .count()).isEqualTo(1);
        assertThat(number(results.get(0), "$.id")).isEqualTo(number(results.get(1), "$.id"));
        assertThat(orderCount(alice)).isEqualTo(1);
        assertThat(stockOf(designing)).isEqualTo(4);
        assertThat(balanceInDatabase(alice)).isEqualTo(150);
    }

    @Test
    void twoDifferentCheckoutsOfOneUserNeverSpendTheSamePointsTwice() throws Exception {
        grantPoints(alice, 100);
        addToCart(alice, designing, 1);

        List<Integer> statuses = runConcurrently(Arrays.asList(
                () -> checkoutStatus(alice, key(), body(aliceAddress, "UPI", 100)),
                () -> checkoutStatus(alice, key(), body(aliceAddress, "UPI", 100))));

        // The cart is consumed by whichever checkout runs first; the other one finds it empty.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(orderCount(alice)).isEqualTo(1);
        assertThat(balanceInDatabase(alice)).isZero();
        assertThat(redeemEntries(alice)).isEqualTo(1);
        assertThat(stockOf(designing)).isEqualTo(4);
    }

    @Test
    void sequentialCheckoutsCannotSpendMorePointsThanTheBalance() throws Exception {
        grantPoints(alice, 100);
        addToCart(alice, designing, 1);
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 100))).andExpect(status().isCreated());

        addToCart(alice, designing, 1);
        mockMvc.perform(checkout(alice, key(), body(aliceAddress, "UPI", 1)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INVALID_POINTS_REDEMPTION"));
        assertThat(balanceInDatabase(alice)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private record TestUser(String token, long id) {
    }

    private static MockHttpServletRequestBuilder checkout(TestUser user, String idempotencyKey, String body) {
        return post("/api/checkout").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int checkoutStatus(TestUser user, String idempotencyKey, String body) throws Exception {
        return mockMvc.perform(checkout(user, idempotencyKey, body)).andReturn().getResponse().getStatus();
    }

    private static String body(long addressId, String method, Integer points) {
        return "{\"addressId\":" + addressId + ",\"paymentMethod\":\"" + method + "\""
                + (points == null ? "" : ",\"pointsToRedeem\":" + points) + "}";
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private static long number(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private void addToCart(TestUser user, long bookId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}"))
            .andExpect(status().is2xxSuccessful());
    }

    private long createAddress(TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientName":"Asha Rao","phone":"+91 98765 43210","line1":"12 MG Road","line2":"Flat 4B",
                         "city":"Pune","state":"Maharashtra","postalCode":"411001"}
                        """))
            .andExpect(status().isCreated()).andReturn();
        return number(result, "$.id");
    }

    /** Adds points the way staff would: an ADJUST ledger entry (the database trigger updates the balance). */
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

    private long orderCount(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Long.class, user.id());
    }

    private long redeemEntries(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM gift_point_transactions WHERE type = 'REDEEM' AND user_id = ?",
                Long.class, user.id());
    }

    private long cartLineCount(TestUser user) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = ?
                """, Long.class, user.id());
    }

    /** A rejected checkout must leave stock, balance, cart and orders exactly as they were. */
    private void assertNothingWasOrdered(TestUser user, long bookId, int expectedStock, int expectedBalance) {
        assertThat(orderCount(user)).isZero();
        assertThat(stockOf(bookId)).isEqualTo(expectedStock);
        assertThat(balanceInDatabase(user)).isEqualTo(expectedBalance);
        assertThat(cartLineCount(user)).isGreaterThan(0);
        assertThat(redeemEntries(user)).isZero();
    }

    /** Starts all tasks at the same moment and returns their results in the order given. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private TestUser registerAndLogin() throws Exception {
        String email = "checkout-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
                VALUES (?, ?, 'checkout test', ?, ?, ?, ?) RETURNING id
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
