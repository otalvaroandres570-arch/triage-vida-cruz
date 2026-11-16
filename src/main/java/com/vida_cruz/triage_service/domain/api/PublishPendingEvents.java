package com.vida_cruz.triage_service.domain.api;

import reactor.core.publisher.Mono;

public interface PublishPendingEvents {

    /**
     * Publishes one batch of pending events in order and emits how many were published.
     * Stops at the first failure: later events stay pending so the order is never broken.
     */
    Mono<Integer> publishBatch();
}