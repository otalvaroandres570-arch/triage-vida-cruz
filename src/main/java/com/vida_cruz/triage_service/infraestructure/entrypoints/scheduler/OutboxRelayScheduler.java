package com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler;

import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Runs the outbox relay periodically. Batches never overlap: if one takes longer than the
 * interval, the ticks that arrive meanwhile are dropped instead of piling up.
 */
@Slf4j
public class OutboxRelayScheduler implements SmartLifecycle {

    private final PublishPendingEvents publishPendingEvents;
    private final Duration interval;
    private Disposable subscription;

    public OutboxRelayScheduler(PublishPendingEvents publishPendingEvents, Duration interval) {
        this.publishPendingEvents = Objects.requireNonNull(publishPendingEvents);
        this.interval = Objects.requireNonNull(interval);
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        log.info("Starting outbox relay (every {})", interval);
        subscription = Flux.interval(interval)
                .onBackpressureDrop(tick -> log.debug("Outbox relay still busy, skipping tick {}", tick))
                .concatMap(tick -> runBatch(), 1)
                .subscribe(
                        published -> { },
                        error -> log.error("Outbox relay terminated unexpectedly", error));
    }

    @Override
    public synchronized void stop() {
        if (subscription != null) {
            subscription.dispose();
            subscription = null;
            log.info("Outbox relay stopped");
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return subscription != null && !subscription.isDisposed();
    }

    private Mono<Integer> runBatch() {
        return publishPendingEvents.publishBatch()
                .doOnNext(published -> {
                    if (published > 0) {
                        log.info("Published {} outbox event(s)", published);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Outbox relay batch failed, retrying on the next tick: {}", error.toString());
                    return Mono.just(0);
                });
    }
}