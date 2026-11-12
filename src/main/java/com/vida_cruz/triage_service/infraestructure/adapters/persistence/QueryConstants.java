package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

public class QueryConstants {

    public static final String INSERT_PATIENT = """
            INSERT INTO patient (
                id, full_name, age, heart_rate, systolic_bp, diastolic_bp, spo2, temperature, pain, gcs,
                chief_complaint, triage_level, sla_minutes, status, escalated, escalated_at,
                registered_at, status_changed_at, consultation_started_at)
            VALUES (
                :id, :fullName, :age, :heartRate, :systolicBp, :diastolicBp, :spo2, :temperature, :pain, :gcs,
                :chiefComplaint, :triageLevel, :slaMinutes, :status, :escalated, :escalatedAt,
                :registeredAt, :statusChangedAt, :consultationStartedAt)
            """;

    public static final String INSERT_AUDIT = """
            INSERT INTO patient_audit (patient_id, previous_status, new_status, event_type, occurred_at)
            VALUES (:patientId, :previousStatus, :newStatus, :eventType, :occurredAt)
            """;

    public static final String SELECT_BY_ID = "SELECT * FROM patient WHERE id = :id";

    public static final String UPDATE_STATUS = """
            UPDATE patient
               SET status = :newStatus,
                   status_changed_at = :statusChangedAt,
                   consultation_started_at = :consultationStartedAt
             WHERE id = :id AND status = :expectedStatus
            """;
}
