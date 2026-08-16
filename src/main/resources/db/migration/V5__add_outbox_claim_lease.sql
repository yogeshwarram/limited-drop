ALTER TABLE outbox_events
    ADD COLUMN claim_owner VARCHAR(36) NULL,
    ADD COLUMN claimed_until TIMESTAMP(6) NULL;
