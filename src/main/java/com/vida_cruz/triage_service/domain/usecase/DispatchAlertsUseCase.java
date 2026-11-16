package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.DispatchAlerts;
import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.RetryBackoff;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import jakarta.annotation.Nonnull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class DispatchAlertsUseCase implements DispatchAlerts {

    private static final int MAX_ERROR_LENGTH = 500;

    private final AlertQueue queue;
    private final AlertNotifier notifier;
    private final Clock clock;
    private final int batchSize;
    private final Duration lease;
    private final RetryBackoff backoff;

    public DispatchAlertsUseCase(AlertQueue queue, AlertNotifier notifier, Clock clock,
                                 int batchSize, Duration lease, RetryBackoff backoff) {
        this.queue = Objects.requireNonNull(queue);
        this.notifier = Objects.requireNonNull(notifier);
        this.clock = Objects.requireNonNull(clock);
        this.batchSize = Optional.of(batchSize)
                .filter(size -> size > 0)
                .orElseThrow(() -> new IllegalArgumentException("batchSize must be positive"));
        this.lease = Objects.requireNonNull(lease);
        this.backoff = Objects.requireNonNull(backoff);
    }

    @Override
    public Mono<Integer> dispatchDue() {
        return Mono.fromSupplier(clock::instant)
                .flatMapMany(this::getEscalationAlertFlux)
                .count()
                .map(Math::toIntExact);
    }

    @Nonnull
    private Flux<EscalationAlert> getEscalationAlertFlux(Instant now) {
        return queue.claimDue(now, batchSize, now.plus(lease))
                .collectSortedList(Comparator.comparing(EscalationAlert::escalatedAt))
                .flatMapMany(Flux::fromIterable)
                .concatMap(alert -> deliver(alert, now));
    }

    /** Emits the alert if it was delivered. A failure schedules a retry and emits nothing, so the rest continue. */
    private Mono<EscalationAlert> deliver(EscalationAlert alert, Instant now) {
        return notifier.notify(alert)
                .then(Mono.defer(() -> queue.markSent(alert.patientId(), clock.instant())))
                .thenReturn(alert)
                .onErrorResume(error -> scheduleRetry(alert, now, error).then(Mono.<EscalationAlert>empty()));
    }

    private Mono<Void> scheduleRetry(EscalationAlert alert, Instant now, Throwable error) {
        int attempts = alert.attempts() + 1;
        return queue.markFailed(alert.patientId(), attempts, now.plus(backoff.delayAfter(attempts)), describe(error));
    }

    private static String describe(Throwable error) {
        String text = Optional.ofNullable(error.getMessage())
                .filter(message -> !message.isBlank())
                .orElseGet(() -> error.getClass().getSimpleName());
        return text.substring(0, Math.min(MAX_ERROR_LENGTH, text.length()));
    }
}