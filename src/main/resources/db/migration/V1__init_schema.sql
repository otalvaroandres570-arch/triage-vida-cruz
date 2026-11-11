CREATE TABLE patient (
                         id                      UUID PRIMARY KEY,
                         full_name               VARCHAR(100)  NOT NULL,
                         age                     SMALLINT      NOT NULL CHECK (age BETWEEN 0 AND 120),
                         heart_rate              SMALLINT      NOT NULL CHECK (heart_rate BETWEEN 20 AND 250),
                         systolic_bp             SMALLINT      NOT NULL CHECK (systolic_bp BETWEEN 60 AND 260),
                         diastolic_bp            SMALLINT      NOT NULL CHECK (diastolic_bp BETWEEN 30 AND 160),
                         spo2                    SMALLINT      NOT NULL CHECK (spo2 BETWEEN 70 AND 100),
                         temperature             NUMERIC(3,1)  NOT NULL CHECK (temperature BETWEEN 34.0 AND 42.0),
                         pain                    SMALLINT      NOT NULL CHECK (pain BETWEEN 0 AND 10),
                         gcs                     SMALLINT      NOT NULL CHECK (gcs BETWEEN 3 AND 15),
                         chief_complaint         VARCHAR(500)  NOT NULL,
                         triage_level            SMALLINT      NOT NULL CHECK (triage_level BETWEEN 1 AND 5),
                         sla_minutes             INTEGER       NOT NULL,
                         status                  VARCHAR(20)   NOT NULL CHECK (status IN ('REGISTRADO', 'EN_CONSULTA', 'ALTA')),
                         escalated               BOOLEAN       NOT NULL DEFAULT FALSE,
                         escalated_at            TIMESTAMPTZ,
                         registered_at           TIMESTAMPTZ   NOT NULL,
                         status_changed_at       TIMESTAMPTZ   NOT NULL,
                         consultation_started_at TIMESTAMPTZ,
                         CONSTRAINT chk_diastolic_lt_systolic CHECK (diastolic_bp < systolic_bp)
);

CREATE INDEX idx_patient_sla_monitor ON patient (registered_at)
    WHERE status = 'REGISTRADO' AND escalated = FALSE;

CREATE INDEX idx_patient_registered_at ON patient (registered_at);

CREATE TABLE patient_audit (
                               id              BIGSERIAL PRIMARY KEY,
                               patient_id      UUID         NOT NULL REFERENCES patient (id),
                               previous_status VARCHAR(20),
                               new_status      VARCHAR(20)  NOT NULL,
                               event_type      VARCHAR(30)  NOT NULL,
                               occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_patient_audit_patient ON patient_audit (patient_id, occurred_at);