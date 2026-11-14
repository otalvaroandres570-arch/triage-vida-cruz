package com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.SyntheticPastData;
import com.vida_cruz.triage_service.domain.api.EscalateOverduePatients;
import com.vida_cruz.triage_service.domain.spi.*;
import com.vida_cruz.triage_service.domain.usecase.EscalateOverduePatientsUseCase;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Real API + real PostgreSQL. Patients are registered through the API and then moved to 1999 so the
 * monitor, run with a clock fixed in 1999, only sees them (the periodic job is disabled in tests).
 */
class SlaMonitorEndToEndIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("1999-01-01T00:00:00Z");
    private static final Instant SLA_EXPIRED = REGISTERED_AT.plusSeconds(6 * 60); // critical SLA is 5 minutes

    private static final String CRITICAL_PATIENT = """
            {"fullName":"Juan Pérez","age":40,"heartRate":130,"systolicBp":180,"diastolicBp":110,
             "spo2":97,"temperature":36.8,"pain":8,"gcs":15,"chiefComplaint":"Dolor torácico"}
            """;

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private EscalationRepository escalationRepository;

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private TransactionRunner transaction;

    @Autowired
    private AlertQueue alertQueue;

    @Autowired private TriageMetrics metrics;

    @BeforeEach
    void cleanSyntheticData() {
        SyntheticPastData.clean(databaseClient);
    }

    @Test
    void shouldEscalateAnOverduePatientEndToEnd() {
        UUID id = registerPatientInThePast();

        StepVerifier.create(monitorAt(SLA_EXPIRED).escalateOverdue()).expectNext(1).verifyComplete();

        client.get().uri("/triage/pacientes/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.escalado").isEqualTo(true)
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.fechaEscalamiento").isNotEmpty();

        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(id));
        assertEquals(List.of("PATIENT_REGISTERED", "PATIENT_ESCALATED"), outboxEventTypes(id));

        Map<String, Object> escalationEvent = outboxRows(id).get(1);
        assertEquals("REGISTRADO", escalationEvent.get("status"));
        assertEquals("REGISTRADO", escalationEvent.get("previous_status"));
        assertEquals("CRITICO", escalationEvent.get("level"));
    }

    @Test
    void shouldNotEscalateWhileThePatientIsWithinTheSla() {
        UUID id = registerPatientInThePast();

        StepVerifier.create(monitorAt(REGISTERED_AT.plusSeconds(4 * 60)).escalateOverdue())
                .expectNext(0)
                .verifyComplete();

        assertEquals(List.of("REGISTRO"), auditEventTypes(id));
    }

    @Test
    void shouldEscalateOnlyOnceAcrossRepeatedRuns() {
        UUID id = registerPatientInThePast();
        EscalateOverduePatients monitor = monitorAt(SLA_EXPIRED);

        StepVerifier.create(monitor.escalateOverdue()).expectNext(1).verifyComplete();
        StepVerifier.create(monitor.escalateOverdue()).expectNext(0).verifyComplete();
        StepVerifier.create(monitor.escalateOverdue()).expectNext(0).verifyComplete();

        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(id));
        assertEquals(List.of("PATIENT_REGISTERED", "PATIENT_ESCALATED"), outboxEventTypes(id));
        assertEquals(1L, alertCount(id));
    }

    @Test
    void shouldNeverEscalateAPatientInConsultationOrDischarged() {
        UUID inConsultation = registerPatientInThePast();
        UUID discharged = registerPatientInThePast();
        patchStatus(inConsultation, "EN_CONSULTA");
        patchStatus(discharged, "ALTA");

        StepVerifier.create(monitorAt(SLA_EXPIRED).escalateOverdue()).expectNext(0).verifyComplete();

        client.get().uri("/triage/pacientes/{id}", inConsultation).exchange()
                .expectBody().jsonPath("$.escalado").isEqualTo(false);
        client.get().uri("/triage/pacientes/{id}", discharged).exchange()
                .expectBody().jsonPath("$.escalado").isEqualTo(false);
    }

    @Test
    void shouldKeepTheEscalationFlagWhenTheEscalatedPatientIsAttended() {
        UUID id = registerPatientInThePast();
        monitorAt(SLA_EXPIRED).escalateOverdue().block();

        patchStatus(id, "EN_CONSULTA");

        client.get().uri("/triage/pacientes/{id}", id).exchange()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.escalado").isEqualTo(true);
        assertEquals(List.of("REGISTRO", "ESCALAMIENTO", "ENTRADA_A_CONSULTA"), auditEventTypes(id));
    }

    @Test
    void shouldEscalateSeveralOverduePatientsInOneRun() {
        UUID first = registerPatientInThePast();
        UUID second = registerPatientInThePast();

        StepVerifier.create(monitorAt(SLA_EXPIRED).escalateOverdue()).expectNext(2).verifyComplete();

        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(first));
        assertEquals(List.of("REGISTRO", "ESCALAMIENTO"), auditEventTypes(second));
    }

    // ---------- helpers ----------

    private EscalateOverduePatients monitorAt(Instant now) {
        return new EscalateOverduePatientsUseCase(escalationRepository, outbox, alertQueue, transaction, metrics,
                Clock.fixed(now, ZoneOffset.UTC), 100);
    }

    private UUID registerPatientInThePast() {
        PatientResponse response = client.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CRITICAL_PATIENT)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PatientResponse.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(response);

        databaseClient.sql("UPDATE patient SET registered_at = :at, status_changed_at = :at WHERE id = :id")
                .bind("at", REGISTERED_AT.atOffset(ZoneOffset.UTC))
                .bind("id", response.id())
                .fetch().rowsUpdated().block();
        return response.id();
    }

    private void patchStatus(UUID id, String status) {
        client.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"estado\":\"%s\"}".formatted(status))
                .exchange()
                .expectStatus().isOk();
    }

    private List<String> auditEventTypes(UUID patientId) {
        return databaseClient.sql("SELECT event_type FROM patient_audit WHERE patient_id = :id ORDER BY id")
                .bind("id", patientId)
                .map(row -> row.get("event_type", String.class))
                .all()
                .collectList()
                .block();
    }

    private List<Map<String, Object>> outboxRows(UUID patientId) {
        return databaseClient.sql("SELECT * FROM outbox_event WHERE patient_id = :id ORDER BY seq")
                .bind("id", patientId)
                .fetch()
                .all()
                .collectList()
                .block();
    }

    private List<String> outboxEventTypes(UUID patientId) {
        return outboxRows(patientId).stream().map(row -> (String) row.get("event_type")).toList();
    }
    private Long alertCount(UUID patientId) {
        return databaseClient.sql("SELECT COUNT(*) FROM escalation_alert WHERE patient_id = :id")
                .bind("id", patientId)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
    }
}