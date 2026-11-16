package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcAlertQueue implements AlertQueue {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> enqueue(EscalationAlert alert, Instant now) {
        return databaseClient.sql(QueryConstants.INSERT_ALERT)
                .bind("patientId", alert.patientId())
                .bind("level", alert.level().name())
                .bind("slaMinutes", alert.slaMinutes())
                .bind("registeredAt", utc(alert.registeredAt()))
                .bind("escalatedAt", utc(alert.escalatedAt()))
                .bind("nextAttemptAt", utc(now))
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Flux<EscalationAlert> claimDue(Instant now, int limit, Instant leaseUntil) {
        return databaseClient.sql(QueryConstants.CLAIM_DUE)
                .bind("now", utc(now))
                .bind("limit", limit)
                .bind("leaseUntil", utc(leaseUntil))
                .map((row, metadata) -> toAlert(row))
                .all();
    }

    @Override
    public Mono<Void> markSent(UUID patientId, Instant sentAt) {
        return databaseClient.sql(QueryConstants.MARK_SENT)
                .bind("patientId", patientId)
                .bind("sentAt", utc(sentAt))
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> markFailed(UUID patientId, int attempts, Instant nextAttemptAt, String error) {
        return databaseClient.sql(QueryConstants.MARK_FAILED)
                .bind("patientId", patientId)
                .bind("attempts", attempts)
                .bind("nextAttemptAt", utc(nextAttemptAt))
                .bind("error", error)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static EscalationAlert toAlert(Row row) {
        return new EscalationAlert(
                row.get("patient_id", UUID.class),
                TriageLevel.valueOf(row.get("triage_level", String.class)),
                row.get("sla_minutes", Integer.class),
                row.get("registered_at", OffsetDateTime.class).toInstant(),
                row.get("escalated_at", OffsetDateTime.class).toInstant(),
                row.get("attempts", Integer.class));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}