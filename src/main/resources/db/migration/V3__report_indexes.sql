-- Supports the operation report: first entry to consultation of each patient.
CREATE INDEX idx_patient_audit_consultation_entry
    ON patient_audit (patient_id, occurred_at)
    WHERE event_type = 'ENTRADA_A_CONSULTA';