ALTER TABLE drops
    MODIFY opens_at DATETIME(6) NOT NULL,
    MODIFY created_at DATETIME(6) NOT NULL;

ALTER TABLE holds
    MODIFY expires_at DATETIME(6) NOT NULL,
    MODIFY created_at DATETIME(6) NOT NULL,
    MODIFY confirmed_at DATETIME(6) NULL,
    MODIFY cancelled_at DATETIME(6) NULL;

ALTER TABLE outbox_events
    MODIFY occurred_at DATETIME(6) NOT NULL,
    MODIFY published_at DATETIME(6) NULL,
    MODIFY claimed_until DATETIME(6) NULL;

ALTER TABLE admin_idempotency_records
    MODIFY created_at DATETIME(6) NOT NULL;

ALTER TABLE admin_audit
    MODIFY occurred_at DATETIME(6) NOT NULL;
