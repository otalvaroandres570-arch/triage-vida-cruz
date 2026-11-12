package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.vida_cruz.triage_service.infraestructure.adapters.persistence.R2dbcValues.mapOrNull;
import static com.vida_cruz.triage_service.infraestructure.adapters.persistence.R2dbcValues.toOffset;

@Component
@RequiredArgsConstructor
public class R2dbcPendingEventStore implements PendingEventStore {

    private static final String SELECT_PENDING = """
            SELECT event_id, event_type, occurred_at, patient_id, level, status, previous_status, sla_minutes
              FROM outbox_event
             WHERE published_at IS NULL
             ORDER BY seq
             LIMIT :limit
            """;

    private static final String MARK_PUBLISHED = """
            UPDATE outbox_event
               SET published_at = :publishedAt
             WHERE event_id = :eventId AND published_at IS NULL
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Flux<TriageEvent> findPending(int limit) {
        return databaseClient.sql(SELECT_PENDING)
                .bind("limit", limit)
                .map((row, metadata) -> toEvent(row))
                .all();
    }

    @Override
    public Mono<Void> markPublished(UUID eventId, Instant publishedAt) {
        return databaseClient.sql(MARK_PUBLISHED)
                .bind("eventId", eventId)
                .bind("publishedAt", toOffset(publishedAt))
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static TriageEvent toEvent(Row row) {
        return new TriageEvent(
                row.get("event_id", UUID.class),
                TriageEventType.valueOf(row.get("event_type", String.class)),
                Objects.requireNonNull(row.get("occurred_at", OffsetDateTime.class)).toInstant(),
                row.get("patient_id", UUID.class),
                TriageLevel.valueOf(row.get("level", String.class)),
                PatientStatus.valueOf(row.get("status", String.class)),
                mapOrNull(row.get("previous_status", String.class), PatientStatus::valueOf),
                row.get("sla_minutes", Integer.class));
    }
}