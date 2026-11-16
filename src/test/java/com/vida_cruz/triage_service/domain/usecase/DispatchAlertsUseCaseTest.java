package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.RetryBackoff;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchAlertsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeAlertQueue queue = new FakeAlertQueue();
    private final List<EscalationAlert> notified = new ArrayList<>();

    @Test
    void shouldDeliverTheDueAlertsAndMarkThemAsSent() {
        EscalationAlert first = alert(0);
        EscalationAlert second = alert(0);
        queue.due.addAll(List.of(first, second));

        StepVerifier.create(useCase(recording()).dispatchDue())
                .expectNext(2)
                .verifyComplete();

        assertEquals(List.of(first, second), notified);
        assertEquals(List.of(first.patientId(), second.patientId()), queue.sent);
    }

    @Test
    void shouldScheduleAnExponentialRetryAndStillDeliverTheOtherAlertsWhenOneFails() {
        EscalationAlert failing = alert(2);
        EscalationAlert healthy = alert(0);
        queue.due.addAll(List.of(failing, healthy));
        AlertNotifier notifier = alert -> alert.equals(failing)
                ? Mono.error(new IllegalStateException("webhook down"))
                : recording().notify(alert);

        StepVerifier.create(useCase(notifier).dispatchDue())
                .expectNext(1)
                .verifyComplete();

        assertEquals(List.of(healthy.patientId()), queue.sent);
        assertEquals(List.of(new FakeAlertQueue.Failure(failing.patientId(), 3, NOW.plusSeconds(20), "webhook down")),
                queue.failures);
    }

    private DispatchAlertsUseCase useCase(AlertNotifier notifier) {
        return new DispatchAlertsUseCase(queue, notifier, CLOCK, 20, Duration.ofSeconds(60),
                new RetryBackoff(Duration.ofSeconds(5), Duration.ofMinutes(5)));
    }

    private AlertNotifier recording() {
        return alert -> Mono.fromRunnable(() -> notified.add(alert));
    }

    private static EscalationAlert alert(int attempts) {
        return new EscalationAlert(UUID.randomUUID(), TriageLevel.CRITICO, 5,
                NOW.minusSeconds(600), NOW.minusSeconds(300), attempts);
    }
}