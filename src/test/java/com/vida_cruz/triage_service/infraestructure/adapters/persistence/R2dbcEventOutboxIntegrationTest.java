package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class R2dbcEventOutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private PatientRepository repository;

    @Autowired
    private TransactionRunner transaction;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldAppendAPendingEventWithAllItsFields() {
        Patient patient = savePatient();
        TriageEvent event = TriageEvent.registration(patient);

        StepVerifier.create(outbox.append(event)).verifyComplete();

        StepVerifier.create(databaseClient.sql("SELECT * FROM outbox_event WHERE event_id = :id")
                        .bind("id", event.eventId())
                        .fetch()
                        .one())
                .assertNext(row -> {
                    assertEquals("PATIENT_REGISTERED", row.get("event_type"));
                    assertEquals(patient.id(), row.get("patient_id"));
                    assertEquals("CRITICO", row.get("level"));
                    assertEquals("REGISTRADO", row.get("status"));
                    assertNull(row.get("previous_status"));
                    assertEquals(5, ((Number) row.get("sla_minutes")).intValue());
                    assertEquals(REGISTERED_AT, ((OffsetDateTime) row.get("occurred_at")).toInstant());
                    assertNull(row.get("published_at"), "a new event must be pending");
                })
                .verifyComplete();
    }

    @Test
    void shouldStorePreviousStatusOfAStatusChange() {
        Patient patient = savePatient();
        Patient inConsultation = patient.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        TriageEvent event = TriageEvent.statusChange(patient, inConsultation);

        StepVerifier.create(outbox.append(event)).verifyComplete();

        StepVerifier.create(databaseClient.sql("SELECT previous_status, status FROM outbox_event WHERE event_id = :id")
                        .bind("id", event.eventId())
                        .fetch()
                        .one())
                .assertNext(row -> {
                    assertEquals("REGISTRADO", row.get("previous_status"));
                    assertEquals("EN_CONSULTA", row.get("status"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectTheSameEventIdTwice() {
        Patient patient = savePatient();
        TriageEvent event = TriageEvent.registration(patient);
        outbox.append(event).block();

        StepVerifier.create(outbox.append(event))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void shouldKeepEventsInInsertionOrder() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        Patient discharged = inConsultation.transitionTo(PatientStatus.ALTA, LATER.plusSeconds(60));

        outbox.append(TriageEvent.registration(registered))
                .then(outbox.append(TriageEvent.statusChange(registered, inConsultation)))
                .then(outbox.append(TriageEvent.statusChange(inConsultation, discharged)))
                .block();

        assertEquals(List.of("PATIENT_REGISTERED", "PATIENT_ENTERED_CONSULTATION", "PATIENT_DISCHARGED"),
                eventTypes(registered.id()));
    }

    @Test
    void shouldCommitPatientAuditAndEventTogether() {
        Patient patient = newPatient();
        TriageEvent event = TriageEvent.registration(patient);

        Mono<Patient> operation = repository.saveWithAudit(patient, PatientAudit.registration(patient))
                .flatMap(saved -> outbox.append(event).thenReturn(saved));

        StepVerifier.create(transaction.inTransaction(operation))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(repository.findById(patient.id())).expectNextCount(1).verifyComplete();
        assertEquals(1L, count("patient_audit", patient.id()));
        assertEquals(1L, count("outbox_event", patient.id()));
    }

    @Test
    void shouldRollBackPatientAndAuditWhenTheEventCannotBeStored() {
        Patient patient = newPatient();
        TriageEvent eventForAnotherPatient = new TriageEvent(UUID.randomUUID(), TriageEventType.PATIENT_REGISTERED,
                REGISTERED_AT, UUID.randomUUID(), TriageLevel.CRITICO, PatientStatus.REGISTRADO, null, 5);

        Mono<Patient> operation = repository.saveWithAudit(patient, PatientAudit.registration(patient))
                .flatMap(saved -> outbox.append(eventForAnotherPatient).thenReturn(saved));

        StepVerifier.create(transaction.inTransaction(operation))
                .expectError(DataIntegrityViolationException.class)
                .verify();

        StepVerifier.create(repository.findById(patient.id())).verifyComplete();
        assertEquals(0L, count("patient_audit", patient.id()));
    }

    // ---------- helpers ----------

    private Patient savePatient() {
        Patient patient = newPatient();
        return repository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private List<String> eventTypes(UUID patientId) {
        return databaseClient.sql("SELECT event_type FROM outbox_event WHERE patient_id = :id ORDER BY seq")
                .bind("id", patientId)
                .map(row -> row.get("event_type", String.class))
                .all()
                .collectList()
                .block();
    }

    private Long count(String table, UUID patientId) {
        return databaseClient.sql("SELECT COUNT(*) FROM " + table + " WHERE patient_id = :id")
                .bind("id", patientId)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
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