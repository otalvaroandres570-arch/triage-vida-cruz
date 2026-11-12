package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterPatientUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryPatientRepository repository;
    private RegisterPatientUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPatientRepository();
        useCase = new RegisterPatientUseCase(
                new TriageClassifier(), SlaPolicy.defaults(), repository, FIXED_CLOCK);
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

        assertEquals(1, repository.savedPatients.size());
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
        RegisterPatientUseCase customUseCase = new RegisterPatientUseCase(
                new TriageClassifier(), new SlaPolicy(custom), repository, FIXED_CLOCK);

        StepVerifier.create(customUseCase.register(criticalPatient()))
                .assertNext(patient -> assertEquals(1, patient.slaMinutes()))
                .verifyComplete();
    }

    @Test
    void shouldSaveRegistrationAuditTogetherWithPatient() {
        Patient registered = useCase.register(criticalPatient()).block();

        assertEquals(1, repository.savedAudits.size());
        PatientAudit audit = repository.savedAudits.getFirst();
        assertNotNull(registered);
        assertEquals(registered.id(), audit.patientId());
        assertNull(audit.previousStatus());
        assertEquals(PatientStatus.REGISTRADO, audit.newStatus());
        assertEquals(AuditEventType.REGISTRO, audit.eventType());
        assertEquals(NOW, audit.occurredAt());
    }

    @Test
    void shouldNotDoAnythingUntilSubscribed() {
        useCase.register(criticalPatient());

        assertTrue(repository.savedPatients.isEmpty());
    }

    @Test
    void shouldPropagateRepositoryFailure() {
        PatientRepository failing = new PatientRepository() {
            @Override
            public Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit) {
                return Mono.error(new IllegalStateException("db down"));
            }

            @Override
            public Mono<Patient> findById(UUID id) {
                return Mono.empty();
            }

            @Override
            public Mono<Patient> updateStatusWithAudit(Patient updated, PatientStatus expectedStatus,
                                                       PatientAudit audit) {
                return Mono.empty();
            }
        };
        RegisterPatientUseCase failingUseCase = new RegisterPatientUseCase(
                new TriageClassifier(), SlaPolicy.defaults(), failing, FIXED_CLOCK);

        StepVerifier.create(failingUseCase.register(criticalPatient()))
                .expectErrorMessage("db down")
                .verify();
    }

    private static RegisterPatientCommand criticalPatient() {
        return new RegisterPatientCommand(
                "Juan Pérez", 40,
                new VitalSigns(130, 180, 110, 97, 36.8, 8, 15),
                "Dolor torácico");
    }

    private static class InMemoryPatientRepository implements PatientRepository {

        final List<Patient> savedPatients = new ArrayList<>();
        final List<PatientAudit> savedAudits = new ArrayList<>();

        @Override
        public Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit) {
            savedPatients.add(patient);
            savedAudits.add(audit);
            return Mono.just(patient);
        }

        @Override
        public Mono<Patient> findById(UUID id) {
            return Mono.empty();
        }

        @Override
        public Mono<Patient> updateStatusWithAudit(Patient updated, PatientStatus expectedStatus,
                                                   PatientAudit audit) {
            return Mono.empty();
        }
    }
}