package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcPatientRepository implements PatientRepository {

    private static final String INSERT_PATIENT = """
            INSERT INTO patient (
                id, full_name, age, heart_rate, systolic_bp, diastolic_bp, spo2, temperature, pain, gcs,
                chief_complaint, triage_level, sla_minutes, status, escalated, escalated_at,
                registered_at, status_changed_at, consultation_started_at)
            VALUES (
                :id, :fullName, :age, :heartRate, :systolicBp, :diastolicBp, :spo2, :temperature, :pain, :gcs,
                :chiefComplaint, :triageLevel, :slaMinutes, :status, :escalated, :escalatedAt,
                :registeredAt, :statusChangedAt, :consultationStartedAt)
            """;

    private static final String INSERT_AUDIT = """
            INSERT INTO patient_audit (patient_id, previous_status, new_status, event_type, occurred_at)
            VALUES (:patientId, :previousStatus, :newStatus, :eventType, :occurredAt)
            """;

    private static final String SELECT_BY_ID = "SELECT * FROM patient WHERE id = :id";

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit) {
        return insertPatient(patient)
                .then(insertAudit(audit))
                .thenReturn(patient)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Patient> findById(UUID id) {
        return databaseClient.sql(SELECT_BY_ID)
                .bind("id", id)
                .map((row, metadata) -> toPatient(row))
                .one();
    }

    private Mono<Long> insertPatient(Patient patient) {
        VitalSigns vitals = patient.vitals();

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_PATIENT)
                .bind("id", patient.id())
                .bind("fullName", patient.fullName())
                .bind("age", patient.age())
                .bind("heartRate", vitals.heartRate())
                .bind("systolicBp", vitals.systolicBp())
                .bind("diastolicBp", vitals.diastolicBp())
                .bind("spo2", vitals.spo2())
                .bind("temperature", BigDecimal.valueOf(vitals.temperature()))
                .bind("pain", vitals.pain())
                .bind("gcs", vitals.gcs())
                .bind("chiefComplaint", patient.chiefComplaint())
                .bind("triageLevel", patient.triageLevel().getValue())
                .bind("slaMinutes", patient.slaMinutes())
                .bind("status", patient.status().name())
                .bind("escalated", patient.escalated());

        spec = bindNullable(spec, "escalatedAt", toOffset(patient.escalatedAt()), OffsetDateTime.class);
        spec = spec.bind("registeredAt", toOffset(patient.registeredAt()))
                .bind("statusChangedAt", toOffset(patient.statusChangedAt()));
        spec = bindNullable(spec, "consultationStartedAt",
                toOffset(patient.consultationStartedAt()), OffsetDateTime.class);

        return spec.fetch().rowsUpdated();
    }

    private Mono<Long> insertAudit(PatientAudit audit) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_AUDIT)
                .bind("patientId", audit.patientId());

        String previousStatus = audit.previousStatus() == null ? null : audit.previousStatus().name();
        spec = bindNullable(spec, "previousStatus", previousStatus, String.class);

        return spec.bind("newStatus", audit.newStatus().name())
                .bind("eventType", audit.eventType().name())
                .bind("occurredAt", toOffset(audit.occurredAt()))
                .fetch()
                .rowsUpdated();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static Patient toPatient(Row row) {
        VitalSigns vitals = new VitalSigns(
                row.get("heart_rate", Integer.class),
                row.get("systolic_bp", Integer.class),
                row.get("diastolic_bp", Integer.class),
                row.get("spo2", Integer.class),
                row.get("temperature", BigDecimal.class).doubleValue(),
                row.get("pain", Integer.class),
                row.get("gcs", Integer.class));

        return Patient.builder()
                .id(row.get("id", UUID.class))
                .fullName(row.get("full_name", String.class))
                .age(row.get("age", Integer.class))
                .vitals(vitals)
                .chiefComplaint(row.get("chief_complaint", String.class))
                .triageLevel(TriageLevel.fromValue(row.get("triage_level", Integer.class)))
                .slaMinutes(row.get("sla_minutes", Integer.class))
                .status(PatientStatus.valueOf(row.get("status", String.class)))
                .escalated(row.get("escalated", Boolean.class))
                .escalatedAt(toInstant(row.get("escalated_at", OffsetDateTime.class)))
                .registeredAt(toInstant(row.get("registered_at", OffsetDateTime.class)))
                .statusChangedAt(toInstant(row.get("status_changed_at", OffsetDateTime.class)))
                .consultationStartedAt(toInstant(row.get("consultation_started_at", OffsetDateTime.class)))
                .build();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}