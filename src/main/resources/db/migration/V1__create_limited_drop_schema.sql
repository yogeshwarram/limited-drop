CREATE TABLE drops (
    id CHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    total_units INT NOT NULL,
    available_units INT NOT NULL,
    opens_at TIMESTAMP(6) NOT NULL,
    hold_duration_seconds INT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_drop_units CHECK (total_units >= 0 AND available_units >= 0 AND available_units <= total_units)
);

CREATE TABLE holds (
    id CHAR(36) NOT NULL PRIMARY KEY,
    drop_id CHAR(36) NOT NULL,
    customer_id VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    state VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    confirmation_idempotency_key VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_hold_drop FOREIGN KEY (drop_id) REFERENCES drops(id),
    CONSTRAINT chk_hold_quantity CHECK (quantity > 0),
    CONSTRAINT chk_hold_state CHECK (state IN ('ACTIVE', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT uq_hold_idempotency UNIQUE (drop_id, customer_id, idempotency_key),
    INDEX idx_holds_expiry (state, expires_at)
);

CREATE TABLE outbox_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_outbox_unpublished (published_at, occurred_at)
);
