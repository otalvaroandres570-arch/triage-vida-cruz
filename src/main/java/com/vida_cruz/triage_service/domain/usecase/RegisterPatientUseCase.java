package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RegisterPatientUseCase implements RegisterPatient {

    private final TriageClassifier classifier;
    private final SlaPolicy slaPolicy;
    private final PatientRepository repository;
    private final Clock clock;

    public RegisterPatientUseCase(TriageClassifier classifier,
                                  SlaPolicy slaPolicy,
                                  PatientRepository repository,
                                  Clock clock) {
        this.classifier = Objects.requireNonNull(classifier);
        this.slaPolicy = Objects.requireNonNull(slaPolicy);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<Patient> register(RegisterPatientCommand command) {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            TriageLevel level = classifier.classify(command.vitals(), command.chiefComplaint());

            Patient patient = Patient.builder()
                    .id(UUID.randomUUID())
                    .fullName(command.fullName())
                    .age(command.age())
                    .vitals(command.vitals())
                    .chiefComplaint(command.chiefComplaint())
                    .triageLevel(level)
                    .slaMinutes(slaPolicy.minutesFor(level))
                    .status(PatientStatus.REGISTRADO)
                    .escalated(false)
                    .registeredAt(now)
                    .statusChangedAt(now)
                    .build();

            return repository.saveWithAudit(patient, PatientAudit.registration(patient));
        });
    }
}