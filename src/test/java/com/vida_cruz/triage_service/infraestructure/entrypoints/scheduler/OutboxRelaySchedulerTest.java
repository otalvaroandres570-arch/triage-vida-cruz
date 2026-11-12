package com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler;

import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRelaySchedulerTest {

    private OutboxRelayScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void shouldRunBatchesPeriodically() {
        AtomicInteger runs = new AtomicInteger();
        PublishPendingEvents useCase = () -> Mono.fromCallable(() -> {
            runs.incrementAndGet();
            return 0;
        });
        scheduler = new OutboxRelayScheduler(useCase, Duration.ofMillis(50));

        scheduler.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 3);
    }

    @Test
    void shouldKeepRunningAfterAFailedBatch() {
        AtomicInteger runs = new AtomicInteger();
        PublishPendingEvents useCase = () -> Mono.defer(() -> {
            if (runs.incrementAndGet() <= 2) {
                return Mono.error(new IllegalStateException("kafka down"));
            }
            return Mono.just(1);
        });
        scheduler = new OutboxRelayScheduler(useCase, Duration.ofMillis(50));

        scheduler.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 5);
        assertTrue(scheduler.isRunning());
    }

    @Test
    void shouldNeverRunTwoBatchesAtTheSameTime() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();
        PublishPendingEvents slowBatch = () -> Mono.defer(() -> {
            runs.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            return Mono.delay(Duration.ofMillis(150))
                    .thenReturn(0)
                    // doOnTerminate, not doFinally: doFinally runs after the completion reaches
                    // concatMap, which may already have subscribed to the next batch -> false "2".
                    .doOnTerminate(inFlight::decrementAndGet);
        });
        scheduler = new OutboxRelayScheduler(slowBatch, Duration.ofMillis(20));

        scheduler.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 3);
        assertEquals(1, maxInFlight.get());
    }

    @Test
    void shouldStopRunningBatchesWhenStopped() throws InterruptedException {
        AtomicInteger runs = new AtomicInteger();
        PublishPendingEvents useCase = () -> Mono.fromCallable(() -> {
            runs.incrementAndGet();
            return 0;
        });
        scheduler = new OutboxRelayScheduler(useCase, Duration.ofMillis(30));
        scheduler.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 1);

        scheduler.stop();
        int runsAfterStop = runs.get();
        Thread.sleep(300);

        assertEquals(runsAfterStop, runs.get());
        assertFalse(scheduler.isRunning());
    }

    @Test
    void shouldReportItsRunningState() {
        scheduler = new OutboxRelayScheduler(() -> Mono.just(0), Duration.ofMillis(50));
        assertFalse(scheduler.isRunning());

        scheduler.start();
        scheduler.start(); // a second start is ignored
        assertTrue(scheduler.isRunning());

        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }
}