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
                .flatMap(current -> Mono.fromCallable(() -> current.transitionTo(target, clock.instant()))
                        .flatMap(updated -> transaction.inTransaction(persistChange(current, updated))));
    }

    /** Saves the new status (guarded by the previous one), its audit and its event; empty means stale. */
    private Mono<Patient> persistChange(Patient current, Patient updated) {
        return repository.updateStatusWithAudit(updated, current.status(), PatientAudit.statusChange(current, updated))
                .flatMap(saved -> outbox.append(TriageEvent.statusChange(current, updated)).thenReturn(saved))
                .switchIfEmpty(Mono.error(() -> new ConcurrentPatientUpdateException(current.id())));
    }
}