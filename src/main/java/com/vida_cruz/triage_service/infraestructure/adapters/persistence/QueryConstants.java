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

    public static final String INSERT_EVENT = """
            INSERT INTO outbox_event (event_id, event_type, occurred_at, patient_id,
                                      level, status, previous_status, sla_minutes)
            VALUES (:eventId, :eventType, :occurredAt, :patientId,
                    :level, :status, :previousStatus, :slaMinutes)
            """;

    public static final String SELECT_OVERDUE = """
            SELECT * FROM patient
             WHERE status = 'REGISTRADO'
               AND escalated = FALSE
               AND registered_at + sla_minutes * INTERVAL '1 minute' < :now
             ORDER BY registered_at
             LIMIT :limit
            """;

    public static final String MARK_ESCALATED = """
            UPDATE patient
               SET escalated = TRUE,
                   escalated_at = :escalatedAt
             WHERE id = :id AND status = 'REGISTRADO' AND escalated = FALSE
            """;

    public static final String HOURLY_OPERATION = """
            WITH patients_in_range AS (
                SELECT id, triage_level, registered_at
                  FROM patient
                 WHERE registered_at >= :rangeStart AND registered_at < :rangeEnd
            ),
            ranked_consultations AS (
                SELECT a.patient_id,
                       a.occurred_at,
                       ROW_NUMBER() OVER (PARTITION BY a.patient_id ORDER BY a.occurred_at, a.id) AS entry_order
                  FROM patient_audit a
                  JOIN patients_in_range p ON p.id = a.patient_id
                 WHERE a.event_type = 'ENTRADA_A_CONSULTA'
            ),
            waits AS (
                SELECT p.triage_level,
                       date_trunc('hour', p.registered_at, 'UTC') AS hour_start,
                       CASE WHEN c.occurred_at IS NOT NULL
                            THEN GREATEST(
                                     EXTRACT(EPOCH FROM (c.occurred_at - p.registered_at))::double precision / 60.0,
                                     0.0::double precision)
                       END AS wait_minutes
                  FROM patients_in_range p
                  LEFT JOIN ranked_consultations c ON c.patient_id = p.id AND c.entry_order = 1
            )
            SELECT hour_start,
                   COUNT(*)                                  AS total,
                   COUNT(*) FILTER (WHERE triage_level = 1)  AS critical,
                   COUNT(*) FILTER (WHERE triage_level = 2)  AS urgent,
                   COUNT(*) FILTER (WHERE triage_level = 3)  AS semi_urgent,
                   COUNT(*) FILTER (WHERE triage_level = 4)  AS non_urgent,
                   COUNT(*) FILTER (WHERE triage_level = 5)  AS administrative,
                   COUNT(wait_minutes)                       AS attended,
                   ROUND(AVG(wait_minutes)::numeric, 2)      AS avg_wait_minutes,
                   ROUND((PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY wait_minutes))::numeric, 2) AS p95_wait_minutes
              FROM waits
             GROUP BY hour_start
             ORDER BY hour_start
            """;
}
