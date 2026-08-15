-- JPA maps String identifiers as VARCHAR. Keep the database schema aligned so
-- Hibernate's production schema validation remains an effective safety check.
ALTER TABLE holds DROP FOREIGN KEY fk_hold_drop;

ALTER TABLE drops MODIFY id VARCHAR(36) NOT NULL;
ALTER TABLE holds MODIFY id VARCHAR(36) NOT NULL;
ALTER TABLE holds MODIFY drop_id VARCHAR(36) NOT NULL;
ALTER TABLE outbox_events MODIFY id VARCHAR(36) NOT NULL;
ALTER TABLE outbox_events MODIFY aggregate_id VARCHAR(36) NOT NULL;

ALTER TABLE holds ADD CONSTRAINT fk_hold_drop FOREIGN KEY (drop_id) REFERENCES drops(id);
