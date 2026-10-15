package com.example.ebookstore.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** Runs the real Flyway migrations on a real PostgreSQL and checks the constraints. */
class SchemaMigrationTest {

    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startDatabaseAndMigrate() throws IOException {
        postgres = EmbeddedPostgres.builder().start();
        dataSource = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        postgres.close();
    }

    /** Clear test data but keep the seeded catalogue. */
    @BeforeEach
    void cleanTransactionalData() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE users CASCADE"); // TRUNCATE does not fire the append-only row triggers
        }
    }

    // ---------------------------------------------------------------- seed data

    @Test
    void seedDataIsLoaded() throws SQLException {
        assertThat(count("categories")).isEqualTo(8);
        assertThat(count("publishers")).isEqualTo(8);
        assertThat(count("books")).isEqualTo(26);
        assertThat(count("authors")).isEqualTo(30);
        assertThat(queryLong("SELECT count(*) FROM book_authors")).isGreaterThanOrEqualTo(20);
        assertThat(queryLong("SELECT count(*) FROM books WHERE price <= 0 OR stock_quantity < 0")).isZero();
    }

    // ---------------------------------------------------------------- users

    @Test
    void rejectsPlainTextPassword() {
        assertThatThrownBy(() -> insertUser("a@example.com", "plain-text-password"))
            .hasMessageContaining("ck_users_password_hash");
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws SQLException {
        insertUser("dup@example.com", BCRYPT_HASH);
        assertThatThrownBy(() -> insertUser("DUP@example.com", BCRYPT_HASH))
            .hasMessageContaining("ux_users_email_lower");
    }

    @Test
    void rejectsNegativePointBalance() throws SQLException {
        long userId = insertUser("p@example.com", BCRYPT_HASH);
        assertThatThrownBy(() -> exec("UPDATE users SET points_balance = -1 WHERE id = " + userId))
            .hasMessageContaining("ck_users_points");
    }

    // ---------------------------------------------------------------- addresses

    @Test
    void allowsOnlyOneDefaultAddressPerUser() throws SQLException {
        long userId = insertUser("addr@example.com", BCRYPT_HASH);
        insertAddress(userId, true);
        insertAddress(userId, false);
        assertThatThrownBy(() -> insertAddress(userId, true))
            .hasMessageContaining("ux_addresses_one_default");
    }

    // ---------------------------------------------------------------- books / cart

    @Test
    void rejectsInvalidBookPriceStockAndIsbn() {
        String base = "INSERT INTO books (title, isbn, price, stock_quantity, category_id, publisher_id) "
            + "VALUES ('T', '%s', %s, %s, (SELECT min(id) FROM categories), (SELECT min(id) FROM publishers))";
        assertThatThrownBy(() -> exec(base.formatted("9781111111111", "-5", "1"))).hasMessageContaining("ck_books_price");
        assertThatThrownBy(() -> exec(base.formatted("9781111111112", "10", "-1"))).hasMessageContaining("ck_books_stock");
        assertThatThrownBy(() -> exec(base.formatted("123", "10", "1"))).hasMessageContaining("ck_books_isbn");
    }

    @Test
    void cartRulesAreEnforced() throws SQLException {
        long userId = insertUser("cart@example.com", BCRYPT_HASH);
        long cartId = insertReturningId("INSERT INTO carts (user_id) VALUES (" + userId + ")");
        long bookId = queryLong("SELECT min(id) FROM books");
        exec("INSERT INTO cart_items (cart_id, book_id, quantity) VALUES (" + cartId + "," + bookId + ", 2)");

        assertThatThrownBy(() -> exec("INSERT INTO carts (user_id) VALUES (" + userId + ")"))
            .hasMessageContaining("uq_carts_user");
        assertThatThrownBy(() -> exec("INSERT INTO cart_items (cart_id, book_id, quantity) VALUES ("
            + cartId + "," + bookId + ", 1)")).hasMessageContaining("uq_cart_items_cart_book");
        assertThatThrownBy(() -> exec("UPDATE cart_items SET quantity = 0")).hasMessageContaining("ck_cart_items_quantity");
    }

    // ---------------------------------------------------------------- orders / payments

    @Test
    void orderSnapshotPaymentAndCancellationRules() throws SQLException {
        long userId = insertUser("order@example.com", BCRYPT_HASH);
        long orderId = insertOrder(userId, "EB-1", "200.00", "0.00", "200.00");
        long bookId = queryLong("SELECT min(id) FROM books");

        exec("INSERT INTO order_items (order_id, book_id, book_title, book_isbn, unit_price, quantity) "
            + "VALUES (" + orderId + "," + bookId + ", 'Snapshot', '9780000000011', 99.50, 2)");
        assertThat(queryString("SELECT line_total::text FROM order_items WHERE order_id = " + orderId)).isEqualTo("199.00");

        // price snapshot is independent of later catalogue price changes
        exec("UPDATE books SET price = 1.00 WHERE id = " + bookId);
        assertThat(queryString("SELECT unit_price::text FROM order_items WHERE order_id = " + orderId)).isEqualTo("99.50");

        // total must equal subtotal - points discount
        assertThatThrownBy(() -> insertOrder(userId, "EB-2", "200.00", "50.00", "200.00"))
            .hasMessageContaining("ck_orders_amounts");
        // cancelled status requires cancelled_at
        assertThatThrownBy(() -> exec("UPDATE orders SET status = 'CANCELLED' WHERE id = " + orderId))
            .hasMessageContaining("ck_orders_cancel_state");
        exec("UPDATE orders SET status = 'CANCELLED', cancelled_at = now() WHERE id = " + orderId);

        // exactly one payment per order, with valid amounts
        exec("INSERT INTO payments (order_id, method, status, amount, paid_at) VALUES ("
            + orderId + ", 'UPI', 'SUCCESS', 200.00, now())");
        assertThatThrownBy(() -> exec("INSERT INTO payments (order_id, method, amount) VALUES ("
            + orderId + ", 'COD', 200.00)")).hasMessageContaining("uq_payments_order");

        // a purchased book cannot be hard-deleted (history is protected)
        assertThatThrownBy(() -> exec("DELETE FROM books WHERE id = " + bookId))
            .hasMessageContaining("fk_order_items_book");
    }

    // ---------------------------------------------------------------- gift points

    @Test
    void giftPointLedgerMaintainsBalanceAndIsAppendOnly() throws SQLException {
        long userId = insertUser("points@example.com", BCRYPT_HASH);
        long orderId = insertOrder(userId, "EB-3", "100.00", "0.00", "100.00");

        exec("INSERT INTO gift_point_transactions (user_id, order_id, type, points) VALUES ("
            + userId + "," + orderId + ", 'EARN', 100)");
        assertThat(queryLong("SELECT points_balance FROM users WHERE id = " + userId)).isEqualTo(100);

        long orderId2 = insertOrder(userId, "EB-4", "100.00", "0.00", "100.00");
        exec("INSERT INTO gift_point_transactions (user_id, order_id, type, points) VALUES ("
            + userId + "," + orderId2 + ", 'REDEEM', -40)");
        assertThat(queryLong("SELECT points_balance FROM users WHERE id = " + userId)).isEqualTo(60);
        assertThat(queryLong("SELECT balance_after FROM gift_point_transactions WHERE type = 'REDEEM'")).isEqualTo(60);

        // cannot redeem more than the balance
        long orderId3 = insertOrder(userId, "EB-5", "100.00", "0.00", "100.00");
        assertThatThrownBy(() -> exec("INSERT INTO gift_point_transactions (user_id, order_id, type, points) VALUES ("
            + userId + "," + orderId3 + ", 'REDEEM', -1000)")).hasMessageContaining("ck_users_points");
        // wrong sign for the type
        assertThatThrownBy(() -> exec("INSERT INTO gift_point_transactions (user_id, order_id, type, points) VALUES ("
            + userId + "," + orderId3 + ", 'REDEEM', 10)")).hasMessageContaining("ck_gpt_sign");
        // same event cannot be recorded twice for one order
        assertThatThrownBy(() -> exec("INSERT INTO gift_point_transactions (user_id, order_id, type, points) VALUES ("
            + userId + "," + orderId + ", 'EARN', 5)")).hasMessageContaining("ux_gpt_once_per_order");
        // ledger rows cannot be edited or deleted
        assertThatThrownBy(() -> exec("UPDATE gift_point_transactions SET points = 1"))
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec("DELETE FROM gift_point_transactions")).hasMessageContaining("append-only");
    }

    // ---------------------------------------------------------------- reviews / updated_at

    @Test
    void reviewsUpdateBookRating() throws SQLException {
        long u1 = insertUser("r1@example.com", BCRYPT_HASH);
        long u2 = insertUser("r2@example.com", BCRYPT_HASH);
        long bookId = queryLong("SELECT min(id) FROM books");
        exec("INSERT INTO reviews (user_id, book_id, rating) VALUES (" + u1 + "," + bookId + ", 5)");
        exec("INSERT INTO reviews (user_id, book_id, rating) VALUES (" + u2 + "," + bookId + ", 4)");

        assertThat(queryString("SELECT rating_avg::text FROM books WHERE id = " + bookId)).isEqualTo("4.50");
        assertThat(queryLong("SELECT rating_count FROM books WHERE id = " + bookId)).isEqualTo(2);
        assertThatThrownBy(() -> exec("INSERT INTO reviews (user_id, book_id, rating) VALUES ("
            + u1 + "," + bookId + ", 3)")).hasMessageContaining("uq_reviews_user_book");
        assertThatThrownBy(() -> exec("INSERT INTO reviews (user_id, book_id, rating) VALUES ("
            + u2 + "," + (bookId + 1) + ", 6)")).hasMessageContaining("ck_reviews_rating");
    }

    @Test
    void updatedAtChangesOnUpdate() throws SQLException, InterruptedException {
        long userId = insertUser("upd@example.com", BCRYPT_HASH);
        Thread.sleep(20);
        exec("UPDATE users SET full_name = 'Changed' WHERE id = " + userId);
        assertThat(queryLong("SELECT count(*) FROM users WHERE id = " + userId + " AND updated_at > created_at"))
            .isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private static long insertUser(String email, String hash) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO users (full_name, email, password_hash) VALUES ('Test User', ?, ?) RETURNING id")) {
            ps.setString(1, email);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertAddress(long userId, boolean isDefault) throws SQLException {
        exec("INSERT INTO addresses (user_id, recipient_name, phone, line1, city, state, postal_code, is_default) "
            + "VALUES (" + userId + ", 'R', '9876543210', '1 Main St', 'Pune', 'MH', '411001', " + isDefault + ")");
    }

    private static long insertOrder(long userId, String number, String subtotal, String discount, String total)
        throws SQLException {
        return insertReturningId("INSERT INTO orders (order_number, user_id, shipping_recipient_name, shipping_phone, "
            + "shipping_line1, shipping_city, shipping_state, shipping_postal_code, subtotal, points_discount, "
            + "total_amount, expected_delivery_date) VALUES ('" + number + "', " + userId
            + ", 'R', '9876543210', '1 Main St', 'Pune', 'MH', '411001', " + subtotal + ", " + discount + ", "
            + total + ", current_date + 5)");
    }

    private static long insertReturningId(String insertSql) throws SQLException {
        return queryLong(insertSql + " RETURNING id");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static long count(String table) throws SQLException {
        return queryLong("SELECT count(*) FROM " + table);
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String queryString(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
