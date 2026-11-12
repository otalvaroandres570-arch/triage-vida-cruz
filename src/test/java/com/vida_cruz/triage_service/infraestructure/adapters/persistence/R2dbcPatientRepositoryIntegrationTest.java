package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class R2dbcPatientRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");

    @Autowired
    private PatientRepository repository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldSaveAndFindPatient() {
        Patient patient = newPatient();

        StepVerifier.create(repository.saveWithAudit(patient, PatientAudit.registration(patient))
                        .flatMap(saved -> repository.findById(saved.id())))
                .expectNext(patient)
                .verifyComplete();
    }

    @Test
    void shouldPersistOptionalTimestamps() {
        Patient patient = newPatient().toBuilder()
                .status(PatientStatus.EN_CONSULTA)
                .escalated(true)
                .escalatedAt(REGISTERED_AT.plusSeconds(300))
                .consultationStartedAt(REGISTERED_AT.plusSeconds(600))
                .statusChangedAt(REGISTERED_AT.plusSeconds(600))
                .build();

        StepVerifier.create(repository.saveWithAudit(patient, PatientAudit.registration(patient))
                        .flatMap(saved -> repository.findById(saved.id())))
                .expectNext(patient)
                .verifyComplete();
    }

    @Test
    void shouldStoreRegistrationAuditAlongsidePatient() {
        Patient patient = newPatient();

        StepVerifier.create(repository.saveWithAudit(patient, PatientAudit.registration(patient))
                        .thenMany(databaseClient.sql("""
                                        SELECT previous_status, new_status, event_type, occurred_at
                                        FROM patient_audit WHERE patient_id = :id
                                        """)
                                .bind("id", patient.id())
                                .fetch()
                                .all()))
                .assertNext(row -> {
                    assertNull(row.get("previous_status"));
                    assertEquals("REGISTRADO", row.get("new_status"));
                    assertEquals("REGISTRO", row.get("event_type"));
                    assertEquals(REGISTERED_AT, ((OffsetDateTime) row.get("occurred_at")).toInstant());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenPatientDoesNotExist() {
        StepVerifier.create(repository.findById(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void shouldRollBackPatientWhenAuditInsertFails() {
        Patient patient = newPatient();
        PatientAudit orphanAudit = new PatientAudit(
                UUID.randomUUID(), null, PatientStatus.REGISTRADO, AuditEventType.REGISTRO, REGISTERED_AT);

        StepVerifier.create(repository.saveWithAudit(patient, orphanAudit))
                .expectError(DataIntegrityViolationException.class)
                .verify();

        StepVerifier.create(repository.findById(patient.id()))
                .verifyComplete();
    }

    private static Patient newPatient() {
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 38.2, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(PatientStatus.REGISTRADO)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}