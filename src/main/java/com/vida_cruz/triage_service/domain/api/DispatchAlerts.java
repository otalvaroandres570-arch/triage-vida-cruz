package com.vida_cruz.triage_service.domain.api;

import reactor.core.publisher.Mono;

public interface DispatchAlerts {

    /** Delivers one batch of due alerts and emits how many were delivered. A failed alert is retried later. */
    Mono<Integer> dispatchDue();
}