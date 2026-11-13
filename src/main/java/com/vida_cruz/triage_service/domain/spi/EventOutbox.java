package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import reactor.core.publisher.Mono;

public interface EventOutbox {

    /**
     * Records the event as pending publication. Meant to run inside the same transaction
     * as the change that originated it, so both are stored or neither is.
     */
    Mono<Void> append(TriageEvent event);
}