package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class FakeAlertQueue implements AlertQueue {

    record Failure(UUID patientId, int attempts, Instant nextAttemptAt, String error) {
    }

    final List<EscalationAlert> enqueued = new ArrayList<>();
    final List<EscalationAlert> due = new ArrayList<>();
    final List<UUID> sent = new ArrayList<>();
    final List<Failure> failures = new ArrayList<>();

    @Override
    public Mono<Void> enqueue(EscalationAlert alert, Instant now) {
        return Mono.fromRunnable(() -> enqueued.add(alert));
    }

    @Override
    public Flux<EscalationAlert> claimDue(Instant now, int limit, Instant leaseUntil) {
        return Flux.defer(() -> Flux.fromIterable(List.copyOf(due)));
    }

    @Override
    public Mono<Void> markSent(UUID patientId, Instant sentAt) {
        return Mono.fromRunnable(() -> sent.add(patientId));
    }

    @Override
    public Mono<Void> markFailed(UUID patientId, int attempts, Instant nextAttemptAt, String error) {
        return Mono.fromRunnable(() -> failures.add(new Failure(patientId, attempts, nextAttemptAt, error)));
    }
}