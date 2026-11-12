package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {

    /**
     * Publishes the event. Completes when the broker has acknowledged it, or emits an
     * {@link com.vida_cruz.triage_service.domain.exceptions.EventPublishingException} if it could not be delivered.
     * Nothing is sent until the returned Mono is subscribed.
     */
    Mono<Void> publish(TriageEvent event);
}