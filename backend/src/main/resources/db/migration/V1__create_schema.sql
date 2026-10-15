-- =====================================================================
-- EBookStore - V1: schema
-- PostgreSQL. Money = NUMERIC (never float). Timestamps = TIMESTAMPTZ.
-- Enum-like columns are VARCHAR + CHECK (maps to JPA @Enumerated(STRING)).
-- =====================================================================

-- ---------- helper: keep updated_at current -------------------------
CREATE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- ---------- 1. users --------------------------------------------------
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    role           VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    points_balance INTEGER      NOT NULL DEFAULT 0,   -- cached; ledger is the audit trail
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email         CHECK (email ~ '^[^@\s]+@[^@\s]+$'),
    -- must look like a modular-crypt hash ($2a$..., $argon2id$...): rejects plain text
    CONSTRAINT ck_users_password_hash CHECK (password_hash ~ '^\$[a-z0-9]+\$.{20,}$'),
    CONSTRAINT ck_users_phone         CHECK (phone IS NULL OR phone ~ '^[0-9+() -]{7,20}$'),
    CONSTRAINT ck_users_role          CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_users_points        CHECK (points_balance >= 0)
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- ---------- 2. addresses ---------------------------------------------
CREATE TABLE addresses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    line1          VARCHAR(150) NOT NULL,
    line2          VARCHAR(150),
    city           VARCHAR(80)  NOT NULL,
    state          VARCHAR(80)  NOT NULL,
    postal_code    VARCHAR(12)  NOT NULL,
    country_code   VARCHAR(2)   NOT NULL DEFAULT 'IN',
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_addresses_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_addresses_phone CHECK (phone ~ '^[0-9+() -]{7,20}$'),
    CONSTRAINT ck_addresses_country CHECK (country_code ~ '^[A-Z]{2}$')
);
CREATE INDEX ix_addresses_user ON addresses (user_id);
-- at most one default address per user
CREATE UNIQUE INDEX ux_addresses_one_default ON addresses (user_id) WHERE is_default;

-- ---------- 3. categories --------------------------------------------
CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_categories_name_lower ON categories (lower(name));

-- ---------- 4. publishers --------------------------------------------
CREATE TABLE publishers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_publishers_name_lower ON publishers (lower(name));

-- ---------- authors (extra: a book can have several authors) ---------
CREATE TABLE authors (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_authors_name_lower ON authors (lower(name));

-- ---------- 5. books --------------------------------------------------
CREATE TABLE books (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title          VARCHAR(255)  NOT NULL,
    isbn           VARCHAR(13)   NOT NULL,
    description    TEXT,
    price          NUMERIC(10,2) NOT NULL,
    stock_quantity INTEGER       NOT NULL DEFAULT 0,
    image_url      VARCHAR(500),
    published_year INTEGER,
    rating_avg     NUMERIC(3,2)  NOT NULL DEFAULT 0,   -- maintained by trigger on reviews
    rating_count   INTEGER       NOT NULL DEFAULT 0,   -- maintained by trigger on reviews
    active         BOOLEAN       NOT NULL DEFAULT TRUE, -- soft delete: books are never hard-deleted
    version        BIGINT        NOT NULL DEFAULT 0,    -- JPA @Version (optimistic locking)
    category_id    BIGINT        NOT NULL,
    publisher_id   BIGINT        NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_books_isbn         UNIQUE (isbn),
    CONSTRAINT fk_books_category     FOREIGN KEY (category_id)  REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_books_publisher    FOREIGN KEY (publisher_id) REFERENCES publishers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_books_isbn         CHECK (isbn ~ '^[0-9]{13}$'),
    CONSTRAINT ck_books_price        CHECK (price > 0),
    CONSTRAINT ck_books_stock        CHECK (stock_quantity >= 0),
    CONSTRAINT ck_books_year         CHECK (published_year IS NULL OR published_year BETWEEN 1400 AND 2100),
    CONSTRAINT ck_books_rating_avg   CHECK (rating_avg BETWEEN 0 AND 5),
    CONSTRAINT ck_books_rating_count CHECK (rating_count >= 0)
);
CREATE INDEX ix_books_category  ON books (category_id);
CREATE INDEX ix_books_publisher ON books (publisher_id);
CREATE INDEX ix_books_title     ON books (lower(title));

-- ---------- book_authors (extra: books <-> authors, many-to-many) ----
CREATE TABLE book_authors (
    book_id   BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_book_authors_book   FOREIGN KEY (book_id)   REFERENCES books (id)   ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author FOREIGN KEY (author_id) REFERENCES authors (id) ON DELETE CASCADE
);
CREATE INDEX ix_book_authors_author ON book_authors (author_id);

-- ---------- 6. carts (one per user) ----------------------------------
CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---------- 7. cart_items --------------------------------------------
CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT      NOT NULL,
    book_id    BIGINT      NOT NULL,
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_cart_book UNIQUE (cart_id, book_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE RESTRICT,
    -- sanity bound only; the per-order limit (10) and stock check live in the service
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 100)
);
CREATE INDEX ix_cart_items_book ON cart_items (book_id);

-- ---------- 8. orders -------------------------------------------------
CREATE TABLE orders (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number            VARCHAR(30)   NOT NULL,
    user_id                 BIGINT        NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    -- delivery address snapshot (copied at checkout; later address edits do not change history)
    shipping_recipient_name VARCHAR(100)  NOT NULL,
    shipping_phone          VARCHAR(20)   NOT NULL,
    shipping_line1          VARCHAR(150)  NOT NULL,
    shipping_line2          VARCHAR(150),
    shipping_city           VARCHAR(80)   NOT NULL,
    shipping_state          VARCHAR(80)   NOT NULL,
    shipping_postal_code    VARCHAR(12)   NOT NULL,
    shipping_country_code   VARCHAR(2)    NOT NULL DEFAULT 'IN',
    -- money
    subtotal                NUMERIC(12,2) NOT NULL,
    points_redeemed         INTEGER       NOT NULL DEFAULT 0,
    points_discount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount            NUMERIC(12,2) NOT NULL,
    points_earned           INTEGER       NOT NULL DEFAULT 0,
    -- dates
    expected_delivery_date  DATE          NOT NULL,
    delivered_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancel_reason           VARCHAR(255),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),   -- order creation time
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_orders_status CHECK (status IN ('PLACED','CONFIRMED','PACKED','SHIPPED','DELIVERED','CANCELLED')),
    CONSTRAINT ck_orders_amounts CHECK (
        subtotal >= 0 AND points_discount >= 0 AND total_amount >= 0
        AND points_redeemed >= 0 AND points_earned >= 0
        AND points_discount <= subtotal
        AND total_amount = subtotal - points_discount),
    CONSTRAINT ck_orders_cancel_state    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT ck_orders_cancel_reason   CHECK (cancel_reason IS NULL OR status = 'CANCELLED'),
    CONSTRAINT ck_orders_cancel_time     CHECK (cancelled_at IS NULL OR cancelled_at >= created_at),
    CONSTRAINT ck_orders_delivered_state CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX ix_orders_status       ON orders (status);

-- ---------- 9. order_items (price / quantity snapshot) ---------------
CREATE TABLE order_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    book_id    BIGINT        NOT NULL,
    book_title VARCHAR(255)  NOT NULL,   -- snapshot
    book_isbn  VARCHAR(13)   NOT NULL,   -- snapshot
    unit_price NUMERIC(10,2) NOT NULL,   -- snapshot of the price paid
    quantity   INTEGER       NOT NULL,
    line_total NUMERIC(12,2) GENERATED ALWAYS AS (unit_price * quantity) STORED,
    CONSTRAINT uq_order_items_order_book UNIQUE (order_id, book_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_book  FOREIGN KEY (book_id)  REFERENCES books (id)  ON DELETE RESTRICT,
    CONSTRAINT ck_order_items_price    CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 100)
);
CREATE INDEX ix_order_items_book ON order_items (book_id);   -- recommendations / buy-again

-- ---------- order_status_history (extra: tracking timeline) ----------
CREATE TABLE order_status_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    note       VARCHAR(255),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_osh_order  FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_osh_status CHECK (status IN ('PLACED','CONFIRMED','PACKED','SHIPPED','DELIVERED','CANCELLED'))
);
CREATE INDEX ix_osh_order_changed ON order_status_history (order_id, changed_at);

-- ---------- 10. payments (simulated gateway, one per order) ----------
CREATE TABLE payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        BIGINT        NOT NULL,
    method          VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    amount          NUMERIC(12,2) NOT NULL,
    transaction_ref VARCHAR(64),
    failure_reason  VARCHAR(255),
    paid_at         TIMESTAMPTZ,
    refunded_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_order  UNIQUE (order_id),   -- exactly one payment per order
    CONSTRAINT fk_payments_order  FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payments_method CHECK (method IN ('CARD','UPI','COD','GIFT_POINTS')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_paid   CHECK (status NOT IN ('SUCCESS','REFUNDED') OR paid_at IS NOT NULL),
    CONSTRAINT ck_payments_refund CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL))
);

-- ---------- 11. gift_point_transactions (append-only audit ledger) ---
CREATE TABLE gift_point_transactions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    order_id      BIGINT,                       -- NULL for manual adjustments
    type          VARCHAR(10)  NOT NULL,
    points        INTEGER      NOT NULL,        -- signed: + adds points, - removes points
    balance_after INTEGER      NOT NULL,        -- filled by trigger; running balance
    description   VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_gpt_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_gpt_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_gpt_type  CHECK (type IN ('EARN','REDEEM','REFUND','ADJUST')),
    CONSTRAINT ck_gpt_nonzero CHECK (points <> 0),
    CONSTRAINT ck_gpt_sign  CHECK (
        (type IN ('EARN','REFUND') AND points > 0)
        OR (type = 'REDEEM' AND points < 0)
        OR type = 'ADJUST'),
    CONSTRAINT ck_gpt_order_required CHECK (type = 'ADJUST' OR order_id IS NOT NULL),
    CONSTRAINT ck_gpt_balance CHECK (balance_after >= 0)
);
CREATE INDEX ix_gpt_user_created ON gift_point_transactions (user_id, created_at DESC);
CREATE INDEX ix_gpt_order        ON gift_point_transactions (order_id);
-- an order can earn, redeem and be refunded points at most once each
CREATE UNIQUE INDEX ux_gpt_once_per_order ON gift_point_transactions (order_id, type)
    WHERE type IN ('EARN','REDEEM','REFUND');

-- Ledger drives the balance: lock the user row, apply the delta, store balance_after.
-- users.points_balance CHECK (>= 0) rejects any overdraw, so the whole insert fails.
CREATE FUNCTION apply_gift_point_transaction() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE users
       SET points_balance = points_balance + NEW.points
     WHERE id = NEW.user_id
    RETURNING points_balance INTO NEW.balance_after;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_gpt_apply_balance
    BEFORE INSERT ON gift_point_transactions
    FOR EACH ROW EXECUTE FUNCTION apply_gift_point_transaction();

-- the ledger is an audit trail: no edits, no deletes
CREATE FUNCTION forbid_ledger_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'gift_point_transactions is append-only';
END;
$$;
CREATE TRIGGER trg_gpt_append_only
    BEFORE UPDATE OR DELETE ON gift_point_transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();

-- ---------- 12. reviews (optional) -----------------------------------
CREATE TABLE reviews (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    book_id    BIGINT       NOT NULL,
    rating     SMALLINT     NOT NULL,
    title      VARCHAR(120),
    comment    VARCHAR(2000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_user_book UNIQUE (user_id, book_id),   -- one review per user per book
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_reviews_book ON reviews (book_id);

-- keep books.rating_avg / rating_count in sync
CREATE FUNCTION refresh_book_rating() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    target BIGINT := COALESCE(NEW.book_id, OLD.book_id);
BEGIN
    UPDATE books b
       SET rating_count = s.cnt,
           rating_avg   = s.avg_rating
      FROM (SELECT count(*) AS cnt, COALESCE(round(avg(rating), 2), 0) AS avg_rating
              FROM reviews WHERE book_id = target) s
     WHERE b.id = target;
    RETURN NULL;
END;
$$;
CREATE TRIGGER trg_reviews_rating
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION refresh_book_rating();

-- ---------- updated_at triggers --------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['users','addresses','categories','publishers','books',
                             'carts','cart_items','orders','payments','reviews']
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_updated_at BEFORE UPDATE ON %1$I
             FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t);
    END LOOP;
END;
$$;
