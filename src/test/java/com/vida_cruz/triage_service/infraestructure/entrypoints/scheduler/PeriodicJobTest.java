package com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicJobTest {

    private PeriodicJob job;

    @AfterEach
    void stopJob() {
        job.stop();
    }

    @Test
    void shouldRunThePeriodicTaskRepeatedly() {
        AtomicInteger runs = new AtomicInteger();
        job = new PeriodicJob("test", Duration.ofMillis(50), () -> Mono.fromSupplier(runs::incrementAndGet));

        job.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 3);
    }

    @Test
    void shouldKeepRunningAfterFailedRuns() {
        AtomicInteger runs = new AtomicInteger();
        job = new PeriodicJob("test", Duration.ofMillis(50), () -> Mono.defer(() ->
                runs.incrementAndGet() <= 2
                        ? Mono.error(new IllegalStateException("kafka down"))
                        : Mono.just(1)));

        job.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 5);
        assertTrue(job.isRunning());
    }

    @Test
    void shouldNeverRunTwoExecutionsAtTheSameTime() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();
        job = new PeriodicJob("test", Duration.ofMillis(20), () -> Mono.defer(() -> {
            runs.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            // doOnTerminate, not doFinally: doFinally runs after the completion reaches concatMap,
            // which may already have started the next execution -> a false "2".
            return Mono.delay(Duration.ofMillis(150)).thenReturn(0).doOnTerminate(inFlight::decrementAndGet);
        }));

        job.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 3);
        assertEquals(1, maxInFlight.get());
    }

    @Test
    void shouldStopRunningWhenStopped() {
        AtomicInteger runs = new AtomicInteger();
        job = new PeriodicJob("test", Duration.ofMillis(30), () -> Mono.fromSupplier(runs::incrementAndGet));
        job.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 1);

        job.stop();
        int runsAfterStop = runs.get();

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> runs.get() == runsAfterStop);
        assertFalse(job.isRunning());
    }

    @Test
    void shouldReportItsRunningStateAndIgnoreASecondStart() {
        job = new PeriodicJob("test", Duration.ofMillis(50), () -> Mono.just(0));
        assertFalse(job.isRunning());

        job.start();
        job.start();
        assertTrue(job.isRunning());

        job.stop();
        job.stop(); // stopping twice is harmless
        assertFalse(job.isRunning());
    }
}