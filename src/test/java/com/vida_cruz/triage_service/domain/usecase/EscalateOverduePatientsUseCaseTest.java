package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalateOverduePatientsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant OVERDUE_SINCE = NOW.minusSeconds(600); // 5 min SLA expired 5 min ago
    private static final Instant WITHIN_SLA = NOW.minusSeconds(120);

    private FakeEscalationRepository repository;
    private FakeEventOutbox outbox;
    private EscalateOverduePatientsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakeEscalationRepository();
        outbox = new FakeEventOutbox();
        useCase = newUseCase(repository, 100);
    }

    @Test
    void shouldEscalateAnOverduePatientAndRecordAuditAndEvent() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));

        StepVerifier.create(useCase.escalateOverdue())
                .expectNext(1)
                .verifyComplete();

        Patient stored = repository.patients.get(patient.id());
        assertTrue(stored.escalated());
        assertEquals(NOW, stored.escalatedAt());
        assertEquals(PatientStatus.REGISTRADO, stored.status(), "escalating does not change the status");

        assertEquals(List.of(AuditEventType.ESCALAMIENTO), repository.audits.stream().map(PatientAudit::eventType).toList());
        assertEquals(1, outbox.events.size());
        TriageEvent event = outbox.events.getFirst();
        assertEquals(TriageEventType.PATIENT_ESCALATED, event.type());
        assertEquals(patient.id(), event.patientId());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void shouldNotEscalateAPatientStillWithinItsSla() {
        repository.seed(TestPatients.in(PatientStatus.REGISTRADO, WITHIN_SLA));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(0).verifyComplete();

        assertTrue(outbox.events.isEmpty());
        assertTrue(repository.audits.isEmpty());
    }

    @ParameterizedTest(name = "a patient in {0} is never escalated")
    @EnumSource(value = PatientStatus.class, names = {"EN_CONSULTA", "ALTA"})
    void shouldNeverEscalateAPatientOutsideTheWaitingState(PatientStatus status) {
        Patient patient = repository.seed(TestPatients.in(status, OVERDUE_SINCE));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(0).verifyComplete();

        assertFalse(repository.patients.get(patient.id()).escalated());
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void shouldEscalateAPatientOnlyOnceNoMatterHowManyTimesItRuns() {
        repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));

        StepVerifier.create(Flux.range(0, 4).concatMap(run -> useCase.escalateOverdue()).collectList())
                .assertNext(counts -> assertEquals(List.of(1, 0, 0, 0), counts))
                .verifyComplete();

        assertEquals(1, outbox.events.size(), "RN-03: a single event");
        assertEquals(1, repository.audits.size(), "RN-03: a single audit entry");
    }

    @Test
    void shouldIgnoreAPatientThatChangedStateAfterItWasSelected() {
        Patient snapshot = TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE);
        FakeEscalationRepository racing = new FakeEscalationRepository() {
            @Override
            public Flux<Patient> findOverdue(Instant now, int limit) {
                return Flux.just(snapshot); // stale view: the patient was selected while still waiting
            }
        };
        racing.seed(snapshot.toBuilder().status(PatientStatus.EN_CONSULTA).build()); // ...and then entered consultation

        StepVerifier.create(newUseCase(racing, 100).escalateOverdue()).expectNext(0).verifyComplete();

        assertTrue(racing.audits.isEmpty());
        assertTrue(outbox.events.isEmpty());
        assertFalse(racing.patients.get(snapshot.id()).escalated());
    }

    @Test
    void shouldEscalateTheOldestRegistrationsFirst() {
        Patient newest = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE.plusSeconds(100)));
        Patient oldest = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE.minusSeconds(100)));
        Patient middle = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(3).verifyComplete();

        assertEquals(List.of(oldest.id(), middle.id(), newest.id()),
                outbox.events.stream().map(TriageEvent::patientId).toList());
    }

    @Test
    void shouldEscalateAtMostOneBatchPerRun() {
        IntStream.range(0, 5).forEach(index ->
                repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE.minusSeconds(index))));
        EscalateOverduePatientsUseCase smallBatches = newUseCase(repository, 2);

        StepVerifier.create(Flux.range(0, 3).concatMap(run -> smallBatches.escalateOverdue()).collectList())
                .assertNext(counts -> assertEquals(List.of(2, 2, 1), counts))
                .verifyComplete();

        assertEquals(5, outbox.events.size());
    }

    @Test
    void shouldKeepWhatWasAlreadyEscalatedWhenALaterPatientFails() {
        Patient first = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE.minusSeconds(60)));
        Patient failing = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));
        repository.failOnPatient = failing.id();

        StepVerifier.create(useCase.escalateOverdue())
                .expectErrorMessage("db failure for " + failing.id())
                .verify();

        assertTrue(repository.patients.get(first.id()).escalated());
        assertFalse(repository.patients.get(failing.id()).escalated());
        assertEquals(1, outbox.events.size());
    }

    @Test
    void shouldPropagateOutboxFailure() {
        repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));
        outbox.failure = new IllegalStateException("outbox down");

        StepVerifier.create(useCase.escalateOverdue())
                .expectErrorMessage("outbox down")
                .verify();
    }

    @Test
    void shouldNotDoAnythingUntilSubscribed() {
        repository.seed(TestPatients.in(PatientStatus.REGISTRADO, OVERDUE_SINCE));

        useCase.escalateOverdue();

        assertTrue(outbox.events.isEmpty());
        assertTrue(repository.audits.isEmpty());
    }

    @ParameterizedTest(name = "batch size {0} is rejected")
    @ValueSource(ints = {0, -3})
    void shouldRejectNonPositiveBatchSize(int batchSize) {
        assertThrows(IllegalArgumentException.class, () -> newUseCase(repository, batchSize));
    }

    private EscalateOverduePatientsUseCase newUseCase(FakeEscalationRepository escalationRepository, int batchSize) {
        return new EscalateOverduePatientsUseCase(escalationRepository, outbox,
                new PassThroughTransactionRunner(), CLOCK, batchSize);
    }
}