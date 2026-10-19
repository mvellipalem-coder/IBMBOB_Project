-- =====================================================================
-- EBookStore - V3: checkout support
--  * order_number_seq   : source of the running number in order numbers (EB-yyyyMMdd-NNNN)
--  * idempotency_keys   : remembers Idempotency-Key values so a retried checkout never creates a second order
--
-- How idempotency works (see docs/CHECKOUT_AND_GIFT_POINTS.md):
--   1. A checkout first locks the user's row, so one user's checkouts run one after the other.
--   2. Inside that lock it looks the key up. Found + same request hash -> replay the stored order.
--      Found + different hash -> 422. Not found -> this is a new checkout.
--   3. The key row is written in the SAME transaction as the order, so committed rows always have an order_id.
--      If the transaction rolls back, the key disappears with it and a retry starts clean.
--   4. The unique constraint is the last line of defence against any race that slips past the lock.
-- =====================================================================

-- Gaps are fine (a rolled-back checkout still consumes a number); uniqueness is what matters.
CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE idempotency_keys (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    operation       VARCHAR(40)  NOT NULL,          -- for example CHECKOUT: keys are scoped to user + operation
    idempotency_key VARCHAR(64)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,          -- SHA-256 (hex) of the normalised request: detects "same key, different request"
    order_id        BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_idem_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_idem_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uq_idem_user_operation_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idem_key  CHECK (idempotency_key ~ '^[A-Za-z0-9_-]{16,64}$'),
    CONSTRAINT ck_idem_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_idem_expires ON idempotency_keys (expires_at);
