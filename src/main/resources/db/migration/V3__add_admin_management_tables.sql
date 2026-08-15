CREATE TABLE admin_idempotency_records (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    actor VARCHAR(200) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_admin_idempotency UNIQUE (actor, operation, idempotency_key)
);

CREATE TABLE admin_audit (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    drop_id VARCHAR(36) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_total_units INT NULL,
    before_available_units INT NULL,
    after_total_units INT NULL,
    after_available_units INT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_admin_audit_drop FOREIGN KEY (drop_id) REFERENCES drops(id),
    INDEX idx_admin_audit_drop_time (drop_id, occurred_at)
);
