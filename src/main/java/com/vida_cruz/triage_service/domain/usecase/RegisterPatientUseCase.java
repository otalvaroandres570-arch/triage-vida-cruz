package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RegisterPatientUseCase implements RegisterPatient {

    private final TriageClassifier classifier;
    private final SlaPolicy slaPolicy;
    private final PatientRepository repository;
    private final EventOutbox outbox;
    private final TransactionRunner transaction;
    private final Clock clock;

    public RegisterPatientUseCase(TriageClassifier classifier,
                                  SlaPolicy slaPolicy,
                                  PatientRepository repository,
                                  EventOutbox outbox,
                                  TransactionRunner transaction,
                                  Clock clock) {
        this.classifier = Objects.requireNonNull(classifier);
        this.slaPolicy = Objects.requireNonNull(slaPolicy);
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<Patient> register(RegisterPatientCommand command) {
        return Mono.fromSupplier(() -> newPatient(command, clock.instant()))
                .flatMap(patient -> transaction.inTransaction(saveWithAuditAndEvent(patient)));
    }

    private Patient newPatient(RegisterPatientCommand command, Instant now) {
        TriageLevel level = classifier.classify(command.vitals(), command.chiefComplaint());
        return Patient.builder()
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
    }

    private Mono<Patient> saveWithAuditAndEvent(Patient patient) {
        return repository.saveWithAudit(patient, PatientAudit.registration(patient))
                .flatMap(saved -> outbox.append(TriageEvent.registration(saved)).thenReturn(saved));
    }
}