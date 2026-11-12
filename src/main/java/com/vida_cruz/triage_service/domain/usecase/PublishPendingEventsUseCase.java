package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class PublishPendingEventsUseCase implements PublishPendingEvents {

    private final PendingEventStore store;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public PublishPendingEventsUseCase(PendingEventStore store, EventPublisher publisher,
                                       Clock clock, int batchSize) {
        this.store = Objects.requireNonNull(store);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.batchSize = Optional.of(batchSize)
                .filter(size -> size > 0)
                .orElseThrow(() -> new IllegalArgumentException("batchSize must be positive"));
    }

    @Override
    public Mono<Integer> publishBatch() {
        return store.findPending(batchSize)
                // Read the whole batch first so the DB cursor is released before talking to Kafka.
                .collectList()
                .flatMapMany(Flux::fromIterable)
                // One at a time, in order: an event is marked published only after Kafka acks it.
                .concatMap(this::publishAndMark)
                .count()
                .map(Long::intValue);
    }

    private Mono<TriageEvent> publishAndMark(TriageEvent event) {
        return publisher.publish(event)
                .then(Mono.defer(() -> store.markPublished(event.eventId(), clock.instant())))
                .thenReturn(event);
    }
}