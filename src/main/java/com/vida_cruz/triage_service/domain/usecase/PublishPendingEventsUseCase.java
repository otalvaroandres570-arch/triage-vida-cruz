package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Objects;

public class PublishPendingEventsUseCase implements PublishPendingEvents {

    private final PendingEventStore store;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public PublishPendingEventsUseCase(PendingEventStore store, EventPublisher publisher,
                                       Clock clock, int batchSize) {
        throwException(batchSize);
        this.store = Objects.requireNonNull(store);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.batchSize = batchSize;
    }

    private static void throwException(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    @Override
    public Mono<Integer> publishBatch() {
        return store.findPending(batchSize)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::publishAndMark)
                .reduce(0, (count, event) -> count + 1);
    }

    private Mono<TriageEvent> publishAndMark(TriageEvent event) {
        return publisher.publish(event)
                .then(Mono.defer(() -> store.markPublished(event.eventId(), clock.instant())))
                .thenReturn(event);
    }
}