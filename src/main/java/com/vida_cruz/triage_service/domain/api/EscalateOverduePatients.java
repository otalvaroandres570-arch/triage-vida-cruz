package com.vida_cruz.triage_service.domain.api;

import reactor.core.publisher.Mono;

public interface EscalateOverduePatients {

    /** Escalates one batch of overdue patients and emits how many were actually escalated. */
    Mono<Integer> escalateOverdue();
}