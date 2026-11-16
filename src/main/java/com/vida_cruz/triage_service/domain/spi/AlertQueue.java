package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface AlertQueue {

    /**
     * Queues the alert, due immediately. Meant to run in the same transaction as the escalation.
     * A patient has at most one alert: queueing it again has no effect.
     */
    Mono<Void> enqueue(EscalationAlert alert, Instant now);

    /**
     * Reserves up to {@code limit} alerts that are due and not sent. A reserved alert is hidden from other
     * callers until {@code leaseUntil}, so each one is delivered by a single caller even with several replicas.
     */
    Flux<EscalationAlert> claimDue(Instant now, int limit, Instant leaseUntil);

    Mono<Void> markSent(UUID patientId, Instant sentAt);

    /** Records a failed attempt and schedules the next one. */
    Mono<Void> markFailed(UUID patientId, int attempts, Instant nextAttemptAt, String error);
}