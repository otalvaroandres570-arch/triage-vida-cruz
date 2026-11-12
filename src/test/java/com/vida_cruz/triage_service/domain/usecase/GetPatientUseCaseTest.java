package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetPatientUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakePatientRepository repository;
    private GetPatientUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new FakePatientRepository();
        useCase = new GetPatientUseCase(repository, CLOCK);
    }

    @Test
    void shouldReturnPatientWithTimeInCurrentStatus() {
        Patient patient = repository.seed(
                TestPatients.in(PatientStatus.REGISTRADO, NOW.minus(Duration.ofMinutes(3))));

        StepVerifier.create(useCase.get(patient.id()))
                .assertNext(details -> {
                    assertEquals(patient, details.patient());
                    assertEquals(Duration.ofMinutes(3), details.timeInCurrentStatus());
                })
                .verifyComplete();
    }

    @Test
    void shouldMeasureTimeFromTheLastStatusChangeAndNotFromRegistration() {
        Instant registeredAt = NOW.minus(Duration.ofMinutes(30));
        Patient inConsultation = TestPatients.in(PatientStatus.EN_CONSULTA, registeredAt).toBuilder()
                .statusChangedAt(NOW.minus(Duration.ofMinutes(4)))
                .build();
        repository.seed(inConsultation);

        StepVerifier.create(useCase.get(inConsultation.id()))
                .assertNext(details -> assertEquals(Duration.ofMinutes(4), details.timeInCurrentStatus()))
                .verifyComplete();
    }

    @Test
    void shouldExposeTheEscalatedFlag() {
        Patient escalated = TestPatients.in(PatientStatus.REGISTRADO, NOW.minus(Duration.ofMinutes(10)))
                .toBuilder()
                .escalated(true)
                .escalatedAt(NOW.minus(Duration.ofMinutes(5)))
                .build();
        repository.seed(escalated);

        StepVerifier.create(useCase.get(escalated.id()))
                .assertNext(details -> assertTrue(details.patient().escalated()))
                .verifyComplete();
    }

    @Test
    void shouldReturnZeroInsteadOfNegativeTimeWhenClocksAreSkewed() {
        Patient patient = repository.seed(TestPatients.in(PatientStatus.REGISTRADO, NOW.plusSeconds(5)));

        StepVerifier.create(useCase.get(patient.id()))
                .assertNext(details -> assertEquals(Duration.ZERO, details.timeInCurrentStatus()))
                .verifyComplete();
    }

    @Test
    void shouldFailWithNotFoundWhenPatientDoesNotExist() {
        StepVerifier.create(useCase.get(UUID.randomUUID()))
                .expectError(PatientNotFoundException.class)
                .verify();
    }
}