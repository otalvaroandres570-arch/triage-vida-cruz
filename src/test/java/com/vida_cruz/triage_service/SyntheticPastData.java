package com.vida_cruz.triage_service;

import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Integration tests share one database. Tests that scan the whole patient table (like the SLA monitor)
 * use patients registered before 2001 and clean them up before each test, so they never see
 * (or alter) the data of other tests.
 */
public final class SyntheticPastData {

    public static final Instant CUTOFF = Instant.parse("2001-01-01T00:00:00Z");

    private static final List<String> STATEMENTS = List.of(
            "DELETE FROM escalation_alert WHERE patient_id IN (SELECT id FROM patient WHERE registered_at < :cutoff)",
            "DELETE FROM outbox_event WHERE patient_id IN (SELECT id FROM patient WHERE registered_at < :cutoff)",
            "DELETE FROM patient_audit WHERE patient_id IN (SELECT id FROM patient WHERE registered_at < :cutoff)",
            "DELETE FROM patient WHERE registered_at < :cutoff");

    private SyntheticPastData() {
    }

    public static void clean(DatabaseClient databaseClient) {
        Flux.fromIterable(STATEMENTS)
                .concatMap(sql -> databaseClient.sql(sql)
                        .bind("cutoff", CUTOFF.atOffset(ZoneOffset.UTC))
                        .fetch()
                        .rowsUpdated())
                .then()
                .block();
    }
}