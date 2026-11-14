package com.vida_cruz.triage_service.infraestructure.adapters.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gauges for the work waiting in the database. They are refreshed by a background job: computing them when the
 * metrics are scraped would block the event loop.
 */
public class PendingWorkGauges {

    private static final String PENDING_WORK = """
            SELECT (SELECT COUNT(*) FROM escalation_alert WHERE sent_at IS NULL) AS pending_alerts,
                   (SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL) AS pending_events
            """;

    private record PendingCounts(long alerts, long events) {
    }

    private final DatabaseClient databaseClient;
    private final AtomicLong pendingAlerts = new AtomicLong();
    private final AtomicLong pendingEvents = new AtomicLong();

    public PendingWorkGauges(DatabaseClient databaseClient, MeterRegistry registry) {
        this.databaseClient = Objects.requireNonNull(databaseClient);
        Gauge.builder("triage.alerts.pending", pendingAlerts, AtomicLong::get)
                .description("Escalation alerts not delivered yet")
                .register(registry);
        Gauge.builder("triage.outbox.pending", pendingEvents, AtomicLong::get)
                .description("Events not published to Kafka yet")
                .register(registry);
    }

    /** Emits 0 so the periodic job does not log a line for each refresh. */
    public Mono<Integer> refresh() {
        return databaseClient.sql(PENDING_WORK)
                .map(row -> new PendingCounts(row.get("pending_alerts", Long.class), row.get("pending_events", Long.class)))
                .one()
                .doOnNext(counts -> {
                    pendingAlerts.set(counts.alerts());
                    pendingEvents.set(counts.events());
                })
                .thenReturn(0);
    }
}