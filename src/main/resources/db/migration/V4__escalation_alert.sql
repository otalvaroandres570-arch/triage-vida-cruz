CREATE TABLE escalation_alert (
                                  patient_id      UUID         PRIMARY KEY REFERENCES patient (id),
                                  triage_level    VARCHAR(20)  NOT NULL,
                                  sla_minutes     INTEGER      NOT NULL,
                                  registered_at   TIMESTAMPTZ  NOT NULL,
                                  escalated_at    TIMESTAMPTZ  NOT NULL,
                                  attempts        INTEGER      NOT NULL DEFAULT 0,
                                  next_attempt_at TIMESTAMPTZ  NOT NULL,
                                  last_error      VARCHAR(500),
                                  sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_escalation_alert_due ON escalation_alert (next_attempt_at) WHERE sent_at IS NULL;