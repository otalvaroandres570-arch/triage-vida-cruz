package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.GetPatient;
import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientDetails;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class GetPatientUseCase implements GetPatient {

    private final PatientRepository repository;
    private final Clock clock;

    public GetPatientUseCase(PatientRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<PatientDetails> get(UUID patientId) {
        return Mono.defer(() -> repository.findById(patientId))
                .switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
                .map(patient -> new PatientDetails(patient, timeInCurrentStatus(patient)));
    }

    private Duration timeInCurrentStatus(Patient patient) {
        Duration elapsed = Duration.between(patient.statusChangedAt(), clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}