package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class R2dbcPendingEventStoreIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @Autowired
    private PendingEventStore store;

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private PatientRepository repository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void cleanOutbox() {
        databaseClient.sql("DELETE FROM outbox_event").fetch().rowsUpdated().block();
    }

    @Test
    void shouldReturnPendingEventsInInsertionOrderWithAllTheirFields() {
        List<TriageEvent> events = seedLifecycleEvents();

        StepVerifier.create(store.findPending(10).collectList())
                .assertNext(found -> assertEquals(events, found))
                .verifyComplete();
    }

    @Test
    void shouldNotReturnPublishedEvents() {
        List<TriageEvent> events = seedLifecycleEvents();
        store.markPublished(events.get(0).eventId(), LATER).block();

        StepVerifier.create(store.findPending(10).collectList())
                .assertNext(found -> assertEquals(List.of(events.get(1), events.get(2)), found))
                .verifyComplete();
    }

    @Test
    void shouldRespectTheLimitReturningTheOldestFirst() {
        List<TriageEvent> events = seedLifecycleEvents();

        StepVerifier.create(store.findPending(2).collectList())
                .assertNext(found -> assertEquals(List.of(events.getFirst(), events.get(1)), found))
                .verifyComplete();
    }

    @Test
    void shouldReturnNothingWhenThereIsNothingPending() {
        StepVerifier.create(store.findPending(10))
                .verifyComplete();
    }

    @Test
    void shouldStoreTheTimeOfPublication() {
        List<TriageEvent> events = seedLifecycleEvents();

        store.markPublished(events.getFirst().eventId(), LATER).block();

        assertEquals(LATER, publishedAt(events.getFirst().eventId()));
    }

    @Test
    void shouldKeepTheFirstPublicationTimeWhenMarkedAgain() {
        List<TriageEvent> events = seedLifecycleEvents();
        UUID eventId = events.getFirst().eventId();

        store.markPublished(eventId, LATER).block();
        store.markPublished(eventId, LATER.plusSeconds(500)).block();

        assertEquals(LATER, publishedAt(eventId));
    }

    @Test
    void shouldIgnoreUnknownEvents() {
        StepVerifier.create(store.markPublished(UUID.randomUUID(), LATER))
                .verifyComplete();
    }

    // ---------- helpers ----------

    private List<TriageEvent> seedLifecycleEvents() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        Patient discharged = inConsultation.transitionTo(PatientStatus.ALTA, LATER.plusSeconds(60));

        List<TriageEvent> events = List.of(
                TriageEvent.registration(registered),
                TriageEvent.statusChange(registered, inConsultation),
                TriageEvent.statusChange(inConsultation, discharged));

        Flux.fromIterable(events).concatMap(outbox::append).then().block();
        return events;
    }

    private Patient savePatient() {
        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 38.2, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(PatientStatus.REGISTRADO)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
        return repository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private Instant publishedAt(UUID eventId) {
        return Objects.requireNonNull(databaseClient.sql("SELECT published_at FROM outbox_event WHERE event_id = :id")
                        .bind("id", eventId)
                        .map(row -> row.get("published_at", OffsetDateTime.class))
                        .one()
                        .block())
                .toInstant();
    }
}