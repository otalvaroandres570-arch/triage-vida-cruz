package com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs a reactive task every {@code interval}. Runs never overlap: ticks that arrive while one is
 * still in progress are dropped, and a failed run is logged and retried on the next tick.
 */
@Slf4j
public class PeriodicJob implements SmartLifecycle {

    private final String name;
    private final Duration interval;
    private final Supplier<Mono<Integer>> task;
    private Disposable subscription; // the only mutable state: the job's lifecycle

    public PeriodicJob(String name, Duration interval, Supplier<Mono<Integer>> task) {
        this.name = Objects.requireNonNull(name);
        this.interval = Objects.requireNonNull(interval);
        this.task = Objects.requireNonNull(task);
    }

    @Override
    public synchronized void start() {
        subscription = isRunning() ? subscription : launch();
    }

    @Override
    public synchronized void stop() {
        Optional.ofNullable(subscription).ifPresent(Disposable::dispose);
        subscription = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return Optional.ofNullable(subscription)
                .map(running -> !running.isDisposed())
                .orElse(false);
    }

    private Disposable launch() {
        log.info("Starting {} (every {})", name, interval);
        return Flux.interval(interval)
                .onBackpressureDrop(tick -> log.debug("{} still busy, skipping tick {}", name, tick))
                .concatMap(tick -> runOnce(), 1)
                .subscribe(
                        processed -> { },
                        error -> log.error("{} terminated unexpectedly", name, error));
    }

    private Mono<Integer> runOnce() {
        return Mono.defer(task)
                .filter(processed -> processed > 0)
                .doOnNext(processed -> log.info("{}: processed {} item(s)", name, processed))
                .onErrorResume(error ->
                        Mono.fromRunnable(() -> log.warn("{} failed, retrying on the next tick: {}", name, error.toString())));
    }
}