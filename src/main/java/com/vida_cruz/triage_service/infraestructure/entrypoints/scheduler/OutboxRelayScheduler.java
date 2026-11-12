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
 * <p>
 * start/stop stay imperative on purpose: they are SmartLifecycle callbacks (synchronous, void)
 * whose only job is to open or dispose the reactive subscription.
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
                .filter(published -> published > 0)
                .doOnNext(published -> log.info("Published {} outbox event(s)", published))
                .doOnError(error -> log.warn("Outbox relay batch failed, retrying on the next tick: {}", error.toString()))
                // Swallow the error so the interval keeps ticking; the events stay pending for the next batch.
                .onErrorResume(error -> Mono.empty());
    }
}