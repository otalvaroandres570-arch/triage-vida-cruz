package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.exceptions.EventPublishingException;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishPendingEventsUseCaseTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-12T17:55:19Z");
    private static final Clock CLOCK = Clock.fixed(OCCURRED_AT.plusSeconds(10), ZoneOffset.UTC);

    private InMemoryPendingEventStore store;
    private RecordingEventPublisher publisher;
    private PublishPendingEventsUseCase useCase;

    @BeforeEach
    void setUp() {
        store = new InMemoryPendingEventStore();
        publisher = new RecordingEventPublisher();
        useCase = new PublishPendingEventsUseCase(store, publisher, CLOCK, 100);
    }

    @Test
    void shouldPublishPendingEventsInOrderAndMarkEachOne() {
        TriageEvent first = pending();
        TriageEvent second = pending();
        TriageEvent third = pending();

        StepVerifier.create(useCase.publishBatch())
                .expectNext(3)
                .verifyComplete();

        assertEquals(List.of(first, second, third), publisher.sent);
        assertEquals(List.of(first.eventId(), second.eventId(), third.eventId()), store.publishedIds);
        assertEquals(CLOCK.instant(), store.publicationTimes.get(first.eventId()));
    }

    @Test
    void shouldDoNothingWhenNothingIsPending() {
        StepVerifier.create(useCase.publishBatch())
                .expectNext(0)
                .verifyComplete();

        assertTrue(publisher.sent.isEmpty());
    }

    @Test
    void shouldStopAtTheFirstFailureAndLeaveTheRestPending() {
        TriageEvent first = pending();
        TriageEvent second = pending();
        TriageEvent third = pending();
        publisher.failOn = second.eventId();

        StepVerifier.create(useCase.publishBatch())
                .expectError(EventPublishingException.class)
                .verify();

        assertEquals(List.of(first), publisher.sent, "the event after the failing one must not be attempted");
        assertEquals(List.of(first.eventId()), store.publishedIds);
        assertEquals(List.of(second, third), store.pendingEvents());
    }

    @Test
    void shouldResumeInOrderOnTheNextRunWithoutRepeatingPublishedEvents() {
        TriageEvent first = pending();
        TriageEvent second = pending();
        TriageEvent third = pending();
        publisher.failOn = second.eventId();
        useCase.publishBatch().onErrorResume(error -> Mono.empty()).block();

        publisher.failOn = null;

        StepVerifier.create(useCase.publishBatch())
                .expectNext(2)
                .verifyComplete();

        assertEquals(List.of(first, second, third), publisher.sent);
        assertTrue(store.pendingEvents().isEmpty());
    }

    @Test
    void shouldRepublishAnEventWhoseMarkingFailed() {
        TriageEvent event = pending();
        store.markFailure = new IllegalStateException("db down");

        StepVerifier.create(useCase.publishBatch())
                .expectErrorMessage("db down")
                .verify();

        assertEquals(List.of(event), publisher.sent);
        assertTrue(store.publishedIds.isEmpty());

        store.markFailure = null;

        StepVerifier.create(useCase.publishBatch())
                .expectNext(1)
                .verifyComplete();

        assertEquals(List.of(event, event), publisher.sent, "delivery is at-least-once");
    }

    @Test
    void shouldPublishAtMostOneBatchPerRun() {
        for (int i = 0; i < 5; i++) {
            pending();
        }
        PublishPendingEventsUseCase smallBatches = new PublishPendingEventsUseCase(store, publisher, CLOCK, 2);

        StepVerifier.create(smallBatches.publishBatch()).expectNext(2).verifyComplete();
        assertEquals(2, store.lastRequestedLimit);

        StepVerifier.create(smallBatches.publishBatch()).expectNext(2).verifyComplete();
        StepVerifier.create(smallBatches.publishBatch()).expectNext(1).verifyComplete();
        assertEquals(5, publisher.sent.size());
    }

    @Test
    void shouldNotDoAnythingUntilSubscribed() {
        pending();

        useCase.publishBatch();

        assertTrue(publisher.sent.isEmpty());
        assertTrue(store.publishedIds.isEmpty());
    }

    @ParameterizedTest(name = "batch size {0} is rejected")
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveBatchSize(int batchSize) {
        assertThrows(IllegalArgumentException.class,
                () -> new PublishPendingEventsUseCase(store, publisher, CLOCK, batchSize));
    }

    private TriageEvent pending() {
        TriageEvent event = new TriageEvent(UUID.randomUUID(), TriageEventType.PATIENT_REGISTERED, OCCURRED_AT,
                UUID.randomUUID(), TriageLevel.CRITICO, PatientStatus.REGISTRADO, null, 5);
        store.all.add(event);
        return event;
    }

    private static class InMemoryPendingEventStore implements PendingEventStore {

        final List<TriageEvent> all = new ArrayList<>();
        final List<UUID> publishedIds = new ArrayList<>();
        final Map<UUID, Instant> publicationTimes = new HashMap<>();
        RuntimeException markFailure;
        Integer lastRequestedLimit;

        List<TriageEvent> pendingEvents() {
            return all.stream().filter(event -> !publishedIds.contains(event.eventId())).toList();
        }

        @Override
        public Flux<TriageEvent> findPending(int limit) {
            return Flux.defer(() -> {
                lastRequestedLimit = limit;
                return Flux.fromIterable(pendingEvents().stream().limit(limit).toList());
            });
        }

        @Override
        public Mono<Void> markPublished(UUID eventId, Instant publishedAt) {
            return Mono.defer(() -> {
                if (markFailure != null) {
                    return Mono.<Void>error(markFailure);
                }
                publishedIds.add(eventId);
                publicationTimes.put(eventId, publishedAt);
                return Mono.<Void>empty();
            });
        }
    }

    private static class RecordingEventPublisher implements EventPublisher {

        final List<TriageEvent> sent = new ArrayList<>();
        UUID failOn;

        @Override
        public Mono<Void> publish(TriageEvent event) {
            return Mono.defer(() -> {
                if (event.eventId().equals(failOn)) {
                    return Mono.<Void>error(new EventPublishingException(event, new IllegalStateException("kafka down")));
                }
                sent.add(event);
                return Mono.<Void>empty();
            });
        }
    }
}