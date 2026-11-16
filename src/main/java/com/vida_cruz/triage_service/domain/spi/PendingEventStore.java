package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PendingEventStore {

    /** Pending events, oldest first (insertion order), at most {@code limit}. */
    Flux<TriageEvent> findPending(int limit);

    /** Marks the event as published. Marking an already published or unknown event has no effect. */
    Mono<Void> markPublished(UUID eventId, Instant publishedAt);
}