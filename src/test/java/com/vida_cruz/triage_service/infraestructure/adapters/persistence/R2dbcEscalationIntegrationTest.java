package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.SyntheticPastData;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EscalationRepository;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcEscalationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant BASE = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant NOW = BASE.plus(Duration.ofMinutes(10));

    @Autowired
    private EscalationRepository escalationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void cleanSyntheticData() {
        SyntheticPastData.clean(databaseClient);
    }

    // ---------- findOverdue ----------

    @Test
    void shouldReturnOnlyWaitingNotEscalatedPatientsPastTheirSla() {
        Patient overdue = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        seed(PatientStatus.REGISTRADO, BASE.plus(Duration.ofMinutes(8)), 5, false); // deadline after NOW
        seed(PatientStatus.EN_CONSULTA, BASE, 5, false);
        seed(PatientStatus.ALTA, BASE, 5, false);
        seed(PatientStatus.REGISTRADO, BASE, 5, true);                               // already escalated

        StepVerifier.create(escalationRepository.findOverdue(NOW, 10).map(Patient::id).collectList())
                .assertNext(ids -> assertEquals(List.of(overdue.id()), ids))
                .verifyComplete();
    }

    @Test
    void shouldReturnTheFullPatientAsItWasStored() {
        Patient overdue = seed(PatientStatus.REGISTRADO, BASE, 5, false);

        StepVerifier.create(escalationRepository.findOverdue(NOW, 10))
                .expectNext(overdue)
                .verifyComplete();
    }

    @Test
    void shouldTreatTheDeadlineAsStrict() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Instant deadline = patient.slaDeadline();

        StepVerifier.create(escalationRepository.findOverdue(deadline, 10)).verifyComplete();
        StepVerifier.create(escalationRepository.findOverdue(deadline.plusMillis(1), 10))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldUseTheSlaOfEachPatient() {
        seed(PatientStatus.REGISTRADO, BASE, 60, false);
        Patient shortSla = seed(PatientStatus.REGISTRADO, BASE, 5, false);

        StepVerifier.create(escalationRepository.findOverdue(NOW, 10).map(Patient::id).collectList())
                .assertNext(ids -> assertEquals(List.of(shortSla.id()), ids))
                .verifyComplete();
    }

    @Test
    void shouldReturnTheOldestRegisteredPatientsFirstUpToTheLimit() {
        Patient oldest = seed(PatientStatus.REGISTRADO, BASE, 1, false);
        Patient middle = seed(PatientStatus.REGISTRADO, BASE.plusSeconds(60), 1, false);
        seed(PatientStatus.REGISTRADO, BASE.plusSeconds(120), 1, false);

        StepVerifier.create(escalationRepository.findOverdue(NOW, 2).map(Patient::id).collectList())
                .assertNext(ids -> assertEquals(List.of(oldest.id(), middle.id()), ids))
                .verifyComplete();
    }

    // ---------- markEscalated ----------

    @Test
    void shouldMarkThePatientEscalatedAndStoreTheAuditEntry() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = patient.escalate(NOW).orElseThrow();

        StepVerifier.create(escalationRepository.markEscalated(escalated, PatientAudit.escalation(escalated)))
                .expectNext(escalated)
                .verifyComplete();

        StepVerifier.create(patientRepository.findById(patient.id()))
                .expectNext(escalated)
                .verifyComplete();
        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(patient.id()));
    }

    @Test
    void shouldStoreTheEscalationAuditWithUnchangedStatusAndEscalationTime() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = patient.escalate(NOW).orElseThrow();

        escalationRepository.markEscalated(escalated, PatientAudit.escalation(escalated)).block();

        StepVerifier.create(databaseClient.sql("""
                                SELECT previous_status, new_status, occurred_at FROM patient_audit
                                WHERE patient_id = :id AND event_type = 'ESCALAMIENTO'
                                """)
                        .bind("id", patient.id())
                        .fetch()
                        .one())
                .assertNext(row -> {
                    assertEquals("REGISTRADO", row.get("previous_status"));
                    assertEquals("REGISTRADO", row.get("new_status"));
                    assertEquals(NOW, ((OffsetDateTime) row.get("occurred_at")).toInstant());
                })
                .verifyComplete();
    }

    @Test
    void shouldNotEscalateTwiceAndNotDuplicateTheAudit() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = patient.escalate(NOW).orElseThrow();
        PatientAudit audit = PatientAudit.escalation(escalated);

        StepVerifier.create(escalationRepository.markEscalated(escalated, audit)).expectNextCount(1).verifyComplete();
        StepVerifier.create(escalationRepository.markEscalated(escalated, audit)).verifyComplete();

        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(patient.id()));
    }

    @ParameterizedTest(name = "a patient in {0} is never marked as escalated")
    @EnumSource(value = PatientStatus.class, names = {"EN_CONSULTA", "ALTA"})
    void shouldNeverEscalateAPatientOutsideTheWaitingState(PatientStatus status) {
        Patient patient = seed(status, BASE, 5, false);
        Patient forcedEscalation = patient.toBuilder().escalated(true).escalatedAt(NOW).build();

        StepVerifier.create(escalationRepository.markEscalated(forcedEscalation,
                        PatientAudit.escalation(forcedEscalation)))
                .verifyComplete();

        StepVerifier.create(patientRepository.findById(patient.id()))
                .assertNext(stored -> assertFalse(stored.escalated()))
                .verifyComplete();
        assertEquals(List.of("REGISTRO"), auditEventTypes(patient.id()));
    }

    @Test
    void shouldLetOnlyOneOfTwoConcurrentEscalationsWin() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = patient.escalate(NOW).orElseThrow();
        PatientAudit audit = PatientAudit.escalation(escalated);

        StepVerifier.create(Flux.merge(
                                escalationRepository.markEscalated(escalated, audit).subscribeOn(Schedulers.parallel()),
                                escalationRepository.markEscalated(escalated, audit).subscribeOn(Schedulers.parallel()))
                        .collectList())
                .assertNext(winners -> assertEquals(1, winners.size()))
                .verifyComplete();

        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(patient.id()));
    }

    @Test
    void shouldRollBackTheFlagWhenTheAuditCannotBeStored() {
        Patient patient = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = patient.escalate(NOW).orElseThrow();
        PatientAudit orphanAudit = new PatientAudit(UUID.randomUUID(), PatientStatus.REGISTRADO,
                PatientStatus.REGISTRADO, AuditEventType.ESCALAMIENTO, NOW);

        StepVerifier.create(escalationRepository.markEscalated(escalated, orphanAudit))
                .expectError(DataIntegrityViolationException.class)
                .verify();

        StepVerifier.create(patientRepository.findById(patient.id()))
                .assertNext(stored -> assertFalse(stored.escalated()))
                .verifyComplete();
    }

    @Test
    void shouldNotAffectOtherPatientsWhenEscalatingOne() {
        Patient target = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient other = seed(PatientStatus.REGISTRADO, BASE, 5, false);
        Patient escalated = target.escalate(NOW).orElseThrow();

        escalationRepository.markEscalated(escalated, PatientAudit.escalation(escalated)).block();

        StepVerifier.create(patientRepository.findById(other.id()))
                .assertNext(stored -> assertTrue(!stored.escalated() && stored.escalatedAt() == null))
                .verifyComplete();
    }

    // ---------- helpers ----------

    private Patient seed(PatientStatus status, Instant registeredAt, int slaMinutes, boolean escalated) {
        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 38.2, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(slaMinutes)
                .status(status)
                .escalated(escalated)
                .escalatedAt(escalated ? registeredAt.plusSeconds(1) : null)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
        return patientRepository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private List<String> auditEventTypes(UUID patientId) {
        return databaseClient.sql("SELECT event_type FROM patient_audit WHERE patient_id = :id ORDER BY id")
                .bind("id", patientId)
                .map(row -> row.get("event_type", String.class))
                .all()
                .collectList()
                .block();
    }
}