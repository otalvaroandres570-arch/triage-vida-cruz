package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class R2dbcEventOutbox implements EventOutbox {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> append(TriageEvent event) {
        return Mono.defer(() -> {
            DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(QueryConstants.INSERT_EVENT)
                    .bind("eventId", event.eventId())
                    .bind("eventType", event.type().name())
                    .bind("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                    .bind("patientId", event.patientId())
                    .bind("level", event.level().name())
                    .bind("status", event.status().name())
                    .bind("slaMinutes", event.slaMinutes());

            spec = event.previousStatus() == null
                    ? spec.bindNull("previousStatus", String.class)
                    : spec.bind("previousStatus", event.previousStatus().name());

            return spec.fetch().rowsUpdated().then();
        });
    }
}