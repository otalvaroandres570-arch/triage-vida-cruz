package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.exceptions.ConcurrentPatientUpdateException;
import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangePatientStatusUseCaseTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant NOW = REGISTERED_AT.plusSeconds(600);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakePatientRepository repository;
    private ChangePatientStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakePatientRepository();
        useCase = new ChangePatientStatusUseCase(repository, CLOCK);
    }

    @Test
    void shouldMoveRegisteredPatientToConsultation() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, REGISTERED_AT));

        StepVerifier.create(useCase.change(patient.id(), PatientStatus.EN_CONSULTA))
                .assertNext(updated -> {
                    assertEquals(PatientStatus.EN_CONSULTA, updated.status());
                    assertEquals(NOW, updated.statusChangedAt());
                    assertEquals(NOW, updated.consultationStartedAt());
                })
                .verifyComplete();

        assertEquals(PatientStatus.EN_CONSULTA, repository.patients.get(patient.id()).status());
        assertEquals(1, repository.audits.size());

        PatientAudit audit = repository.audits.getFirst();
        assertEquals(patient.id(), audit.patientId());
        assertEquals(PatientStatus.REGISTRADO, audit.previousStatus());
        assertEquals(PatientStatus.EN_CONSULTA, audit.newStatus());
        assertEquals(AuditEventType.ENTRADA_A_CONSULTA, audit.eventType());
        assertEquals(NOW, audit.occurredAt());
    }

    @Test
    void shouldAllowDirectDischargeWithoutConsultation() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, REGISTERED_AT));

        StepVerifier.create(useCase.change(patient.id(), PatientStatus.ALTA))
                .assertNext(updated -> {
                    assertEquals(PatientStatus.ALTA, updated.status());
                    assertNull(updated.consultationStartedAt());
                })
                .verifyComplete();

        assertEquals(AuditEventType.ALTA, repository.audits.getFirst().eventType());
    }

    @Test
    void shouldDischargePatientInConsultation() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.EN_CONSULTA, REGISTERED_AT));

        StepVerifier.create(useCase.change(patient.id(), PatientStatus.ALTA))
                .assertNext(updated -> assertEquals(PatientStatus.ALTA, updated.status()))
                .verifyComplete();

        PatientAudit audit = repository.audits.getFirst();
        assertEquals(PatientStatus.EN_CONSULTA, audit.previousStatus());
        assertEquals(PatientStatus.ALTA, audit.newStatus());
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected without side effects")
    @CsvSource({
            "ALTA,EN_CONSULTA",
            "ALTA,ALTA",
            "EN_CONSULTA,REGISTRADO",
            "REGISTRADO,REGISTRADO"
    })
    void shouldRejectInvalidTransitionWithoutWritingAnything(PatientStatus from, PatientStatus to) {
        Patient patient = repository.seed(TestPatients.in(from, REGISTERED_AT));

        StepVerifier.create(useCase.change(patient.id(), to))
                .expectError(InvalidStatusTransitionException.class)
                .verify();

        assertEquals(from, repository.patients.get(patient.id()).status());
        assertTrue(repository.audits.isEmpty());
    }

    @Test
    void shouldRejectChangingStatusOfAnAlreadyDischargedPatient() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, REGISTERED_AT));

        useCase.change(patient.id(), PatientStatus.ALTA).block();

        StepVerifier.create(useCase.change(patient.id(), PatientStatus.EN_CONSULTA))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(InvalidStatusTransitionException.class, error);
                    assertTrue(error.getMessage().contains("ALTA"));
                    assertTrue(error.getMessage().contains("EN_CONSULTA"));
                })
                .verify();

        assertEquals(1, repository.audits.size());
    }

    @Test
    void shouldFailWithNotFoundWhenPatientDoesNotExist() {
        StepVerifier.create(useCase.change(UUID.randomUUID(), PatientStatus.EN_CONSULTA))
                .expectError(PatientNotFoundException.class)
                .verify();
    }

    @Test
    void shouldFailWithConflictWhenStatusChangedConcurrently() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, REGISTERED_AT));
        repository.rejectUpdatesAsStale = true;

        StepVerifier.create(useCase.change(patient.id(), PatientStatus.EN_CONSULTA))
                .expectError(ConcurrentPatientUpdateException.class)
                .verify();

        assertEquals(PatientStatus.REGISTRADO, repository.patients.get(patient.id()).status());
        assertTrue(repository.audits.isEmpty());
    }

    @Test
    void shouldNotDoAnythingUntilSubscribed() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, REGISTERED_AT));

        useCase.change(patient.id(), PatientStatus.EN_CONSULTA);

        assertEquals(PatientStatus.REGISTRADO, repository.patients.get(patient.id()).status());
        assertTrue(repository.audits.isEmpty());
    }
}