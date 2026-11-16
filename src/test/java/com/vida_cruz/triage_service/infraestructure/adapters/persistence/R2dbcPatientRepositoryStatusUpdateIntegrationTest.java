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
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class R2dbcPatientRepositoryStatusUpdateIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @Autowired
    private PatientRepository repository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldUpdateStatusAndStoreAuditEntry() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        PatientAudit audit = PatientAudit.statusChange(registered, inConsultation);

        StepVerifier.create(repository.updateStatusWithAudit(inConsultation, PatientStatus.REGISTRADO, audit)
                        .flatMap(saved -> repository.findById(saved.id())))
                .expectNext(inConsultation)
                .verifyComplete();

        StepVerifier.create(auditEventTypes(registered.id()))
                .expectNext("REGISTRO", "ENTRADA_A_CONSULTA")
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyAndWriteNothingWhenExpectedStatusIsStale() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        repository.updateStatusWithAudit(inConsultation, PatientStatus.REGISTRADO,
                PatientAudit.statusChange(registered, inConsultation)).block();

        Patient staleDischarge = registered.transitionTo(PatientStatus.ALTA, LATER.plusSeconds(60));
        PatientAudit staleAudit = PatientAudit.statusChange(registered, staleDischarge);

        StepVerifier.create(repository.updateStatusWithAudit(staleDischarge, PatientStatus.REGISTRADO, staleAudit))
                .verifyComplete();

        StepVerifier.create(repository.findById(registered.id()))
                .assertNext(patient -> assertEquals(PatientStatus.EN_CONSULTA, patient.status()))
                .verifyComplete();

        StepVerifier.create(auditEventTypes(registered.id()).count())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void shouldLetOnlyOneOfTwoConcurrentChangesWin() {
        Patient registered = savePatient();
        Patient toConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        Patient toDischarge = registered.transitionTo(PatientStatus.ALTA, LATER);

        Flux<Patient> race = Flux.merge(
                repository.updateStatusWithAudit(toConsultation, PatientStatus.REGISTRADO,
                        PatientAudit.statusChange(registered, toConsultation)).subscribeOn(Schedulers.parallel()),
                repository.updateStatusWithAudit(toDischarge, PatientStatus.REGISTRADO,
                        PatientAudit.statusChange(registered, toDischarge)).subscribeOn(Schedulers.parallel()));

        StepVerifier.create(race.collectList())
                .assertNext(winners -> assertEquals(1, winners.size()))
                .verifyComplete();

        StepVerifier.create(auditEventTypes(registered.id()).count())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void shouldRollBackStatusChangeWhenAuditInsertFails() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        PatientAudit orphanAudit = new PatientAudit(UUID.randomUUID(), PatientStatus.REGISTRADO,
                PatientStatus.EN_CONSULTA, AuditEventType.ENTRADA_A_CONSULTA, LATER);

        StepVerifier.create(repository.updateStatusWithAudit(inConsultation, PatientStatus.REGISTRADO, orphanAudit))
                .expectError(DataIntegrityViolationException.class)
                .verify();

        StepVerifier.create(repository.findById(registered.id()))
                .assertNext(patient -> {
                    assertEquals(PatientStatus.REGISTRADO, patient.status());
                    assertNull(patient.consultationStartedAt());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenUpdatingAPatientThatDoesNotExist() {
        Patient ghost = newPatient();
        Patient inConsultation = ghost.transitionTo(PatientStatus.EN_CONSULTA, LATER);

        StepVerifier.create(repository.updateStatusWithAudit(inConsultation, PatientStatus.REGISTRADO,
                        PatientAudit.statusChange(ghost, inConsultation)))
                .verifyComplete();
    }

    private Patient savePatient() {
        Patient patient = newPatient();
        return repository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private Flux<String> auditEventTypes(UUID patientId) {
        return databaseClient.sql("SELECT event_type FROM patient_audit WHERE patient_id = :id ORDER BY id")
                .bind("id", patientId)
                .map(row -> row.get("event_type", String.class))
                .all();
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