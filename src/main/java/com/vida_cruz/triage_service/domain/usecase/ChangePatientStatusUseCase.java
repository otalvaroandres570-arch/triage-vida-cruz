package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.ChangePatientStatus;
import com.vida_cruz.triage_service.domain.exceptions.ConcurrentPatientUpdateException;
import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public class ChangePatientStatusUseCase implements ChangePatientStatus {

    private final PatientRepository repository;
    private final EventOutbox outbox;
    private final TransactionRunner transaction;
    private final Clock clock;

    public ChangePatientStatusUseCase(PatientRepository repository,
                                      EventOutbox outbox,
                                      TransactionRunner transaction,
                                      Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<Patient> change(UUID patientId, PatientStatus target) {
        return Mono.defer(() -> repository.findById(patientId))
                .switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
                .flatMap(current -> applyTransition(current, target));
    }

    private Mono<Patient> applyTransition(Patient current, PatientStatus target) {
        Patient updated = current.transitionTo(target, clock.instant());
        PatientAudit audit = PatientAudit.statusChange(current, updated);
        TriageEvent event = TriageEvent.statusChange(current, updated);

        Mono<Patient> change = repository.updateStatusWithAudit(updated, current.status(), audit)
                .flatMap(saved -> outbox.append(event).thenReturn(saved))
                .switchIfEmpty(Mono.error(new ConcurrentPatientUpdateException(current.id())));

        return transaction.inTransaction(change);
    }
}