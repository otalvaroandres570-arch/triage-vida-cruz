package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.EscalateOverduePatients;
import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import com.vida_cruz.triage_service.domain.spi.EscalationRepository;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import com.vida_cruz.triage_service.domain.spi.TriageMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class EscalateOverduePatientsUseCase implements EscalateOverduePatients {

    private final EscalationRepository repository;
    private final EventOutbox outbox;
    private final AlertQueue alertQueue;
    private final TransactionRunner transaction;
    private final TriageMetrics metrics;
    private final Clock clock;
    private final int batchSize;

    public EscalateOverduePatientsUseCase(EscalationRepository repository,
                                          EventOutbox outbox,
                                          AlertQueue alertQueue,
                                          TransactionRunner transaction,
                                          TriageMetrics metrics,
                                          Clock clock,
                                          int batchSize) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.alertQueue = Objects.requireNonNull(alertQueue);
        this.transaction = Objects.requireNonNull(transaction);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.batchSize = Optional.of(batchSize)
                .filter(size -> size > 0)
                .orElseThrow(() -> new IllegalArgumentException("batchSize must be positive"));
    }

    @Override
    public Mono<Integer> escalateOverdue() {
        return Mono.fromSupplier(clock::instant)
                .flatMapMany(now -> repository.findOverdue(now, batchSize)
                        .collectList() // read the whole batch first so no DB connection stays open while escalating
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(patient -> escalate(patient, now)))
                .count()
                .map(Math::toIntExact);
    }

    /**
     * Each patient is escalated in its own transaction, so one failure never undoes the others.
     * The escalation, its audit entry, its event and its alert are stored together or not at all.
     * The metric is counted only after the transaction has been committed.
     */
    private Mono<Patient> escalate(Patient patient, Instant now) {
        return Mono.justOrEmpty(patient.escalate(now))
                .flatMap(escalated -> transaction.inTransaction(
                                repository.markEscalated(escalated, PatientAudit.escalation(escalated))
                                        .flatMap(saved -> outbox.append(TriageEvent.escalation(saved))
                                                .then(Mono.defer(() -> alertQueue.enqueue(EscalationAlert.of(saved), now)))
                                                .thenReturn(saved)))
                        .doOnNext(committed -> metrics.patientEscalated(committed.triageLevel())));
    }
}