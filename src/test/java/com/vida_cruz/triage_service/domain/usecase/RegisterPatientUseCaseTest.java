package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterPatientUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakePatientRepository repository;
    private FakeEventOutbox outbox;
    private RegisterPatientUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakePatientRepository();
        outbox = new FakeEventOutbox();
        useCase = newUseCase(SlaPolicy.defaults(), repository);
    }

    @Test
    void shouldRegisterCriticalPatientWithItsSla() {
        StepVerifier.create(useCase.register(criticalPatient()))
                .assertNext(patient -> {
                    assertNotNull(patient.id());
                    assertEquals("Juan Pérez", patient.fullName());
                    assertEquals(TriageLevel.CRITICO, patient.triageLevel());
                    assertEquals(5, patient.slaMinutes());
                    assertEquals(PatientStatus.REGISTRADO, patient.status());
                    assertFalse(patient.escalated());
                    assertEquals(NOW, patient.registeredAt());
                    assertEquals(NOW, patient.statusChangedAt());
                    assertNull(patient.escalatedAt());
                    assertNull(patient.consultationStartedAt());
                })
                .verifyComplete();

        assertEquals(1, repository.patients.size());
    }

    @Test
    void shouldApplyAdministrativeComplaintRule() {
        RegisterPatientCommand command = new RegisterPatientCommand(
                "María López", 35,
                new VitalSigns(70, 115, 75, 99, 36.4, 0, 15),
                "Renovación de recetario");

        StepVerifier.create(useCase.register(command))
                .assertNext(patient -> {
                    assertEquals(TriageLevel.NO_URGENTE_2, patient.triageLevel());
                    assertEquals(120, patient.slaMinutes());
                })
                .verifyComplete();
    }

    @Test
    void shouldUseConfiguredSlaInsteadOfDefaults() {
        Map<TriageLevel, Integer> custom = new EnumMap<>(TriageLevel.class);
        for (TriageLevel level : TriageLevel.values()) {
            custom.put(level, 1);
        }

        StepVerifier.create(newUseCase(new SlaPolicy(custom), repository).register(criticalPatient()))
                .assertNext(patient -> assertEquals(1, patient.slaMinutes()))
                .verifyComplete();
    }

    @Test
    void shouldSaveRegistrationAuditEntry() {
        Patient registered = useCase.register(criticalPatient()).block();

        assertEquals(1, repository.audits.size());
        PatientAudit audit = repository.audits.get(0);
        assertEquals(registered.id(), audit.patientId());
        assertNull(audit.previousStatus());
        assertEquals(PatientStatus.REGISTRADO, audit.newStatus());
        assertEquals(AuditEventType.REGISTRO, audit.eventType());
        assertEquals(NOW, audit.occurredAt());
    }

    @Test
    void shouldRecordRegistrationEventForTheOutbox() {
        Patient registered = useCase.register(criticalPatient()).block();

        assertEquals(1, outbox.events.size());
        TriageEvent event = outbox.events.get(0);
        assertNotNull(event.eventId());
        assertEquals(TriageEventType.PATIENT_REGISTERED, event.type());
        assertEquals(registered.id(), event.patientId());
        assertEquals(TriageLevel.CRITICO, event.level());
        assertEquals(PatientStatus.REGISTRADO, event.status());
        assertNull(event.previousStatus());
        assertEquals(5, event.slaMinutes());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void shouldNotDoAnythingUntilSubscribed() {
        useCase.register(criticalPatient());

        assertTrue(repository.patients.isEmpty());
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void shouldPropagateRepositoryFailureWithoutRecordingEvents() {
        FakePatientRepository failing = new FakePatientRepository() {
            @Override
            public Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit) {
                return Mono.error(new IllegalStateException("db down"));
            }
        };

        StepVerifier.create(newUseCase(SlaPolicy.defaults(), failing).register(criticalPatient()))
                .expectErrorMessage("db down")
                .verify();

        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void shouldPropagateOutboxFailure() {
        outbox.failure = new IllegalStateException("outbox down");

        StepVerifier.create(useCase.register(criticalPatient()))
                .expectErrorMessage("outbox down")
                .verify();
    }

    private RegisterPatientUseCase newUseCase(SlaPolicy slaPolicy, PatientRepository patientRepository) {
        return new RegisterPatientUseCase(new TriageClassifier(), slaPolicy, patientRepository,
                outbox, new PassThroughTransactionRunner(), FIXED_CLOCK);
    }

    private static RegisterPatientCommand criticalPatient() {
        return new RegisterPatientCommand(
                "Juan Pérez", 40,
                new VitalSigns(130, 180, 110, 97, 36.8, 8, 15),
                "Dolor torácico");
    }
}