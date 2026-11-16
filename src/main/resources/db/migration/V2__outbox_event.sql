-- Transactional outbox: events are written in the same transaction as the state change
-- and published to Kafka later by a relay. published_at IS NULL means "pending".
CREATE TABLE outbox_event (
                              seq             BIGSERIAL PRIMARY KEY,
                              event_id        UUID         NOT NULL UNIQUE,
                              event_type      VARCHAR(40)  NOT NULL,
                              occurred_at     TIMESTAMPTZ  NOT NULL,
                              patient_id      UUID         NOT NULL REFERENCES patient (id),
                              level           VARCHAR(20)  NOT NULL,
                              status          VARCHAR(20)  NOT NULL,
                              previous_status VARCHAR(20),
                              sla_minutes     INTEGER      NOT NULL,
                              published_at    TIMESTAMPTZ
);

-- Relay lookup: pending events in insertion order.
CREATE INDEX idx_outbox_event_pending ON outbox_event (seq) WHERE published_at IS NULL;

CREATE INDEX idx_outbox_event_patient ON outbox_event (patient_id);
