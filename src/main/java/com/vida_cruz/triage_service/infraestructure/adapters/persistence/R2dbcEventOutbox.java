package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.vida_cruz.triage_service.infraestructure.adapters.persistence.R2dbcValues.nullable;
import static com.vida_cruz.triage_service.infraestructure.adapters.persistence.R2dbcValues.toOffset;

@Component
@RequiredArgsConstructor
public class R2dbcEventOutbox implements EventOutbox {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> append(TriageEvent event) {
        return Mono.defer(() -> databaseClient.sql(QueryConstants.INSERT_EVENT)
                        .bind("eventId", event.eventId())
                        .bind("eventType", event.type().name())
                        .bind("occurredAt", toOffset(event.occurredAt()))
                        .bind("patientId", event.patientId())
                        .bind("level", event.level().name())
                        .bind("status", event.status().name())
                        .bind("previousStatus", nullable(event.previousStatus(), PatientStatus::name, String.class))
                        .bind("slaMinutes", event.slaMinutes())
                        .fetch()
                        .rowsUpdated())
                .then();
    }
}
