package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutboxEndToEndIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CRITICAL_PATIENT = """
            {"fullName":"Juan Pérez","age":40,"heartRate":130,"systolicBp":180,"diastolicBp":110,
             "spo2":97,"temperature":36.8,"pain":8,"gcs":15,"chiefComplaint":"Dolor torácico"}
            """;

    private static final String INVALID_PATIENT = CRITICAL_PATIENT.replace("\"spo2\":97", "\"spo2\":101");

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldRecordAPendingRegistrationEvent() {
        UUID id = register();

        List<Map<String, Object>> rows = outboxRows(id);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.getFirst();
        assertEquals("PATIENT_REGISTERED", row.get("event_type"));
        assertEquals("CRITICO", row.get("level"));
        assertEquals("REGISTRADO", row.get("status"));
        assertNull(row.get("previous_status"));
        assertEquals(5, ((Number) row.get("sla_minutes")).intValue());
        assertNull(row.get("published_at"));
    }

    @Test
    void shouldRecordOneEventPerStatusChangeInOrder() {
        UUID id = register();

        patchStatus(id, "EN_CONSULTA").expectStatus().isOk();
        patchStatus(id, "ALTA").expectStatus().isOk();

        assertEquals(List.of("PATIENT_REGISTERED", "PATIENT_ENTERED_CONSULTATION", "PATIENT_DISCHARGED"),
                outboxRows(id).stream().map(row -> (String) row.get("event_type")).toList());

        Map<String, Object> consultation = outboxRows(id).get(1);
        assertEquals("REGISTRADO", consultation.get("previous_status"));
        assertEquals("EN_CONSULTA", consultation.get("status"));
    }

    @Test
    void shouldRecordDirectDischarge() {
        UUID id = register();

        patchStatus(id, "ALTA").expectStatus().isOk();

        assertEquals(List.of("PATIENT_REGISTERED", "PATIENT_DISCHARGED"),
                outboxRows(id).stream().map(row -> (String) row.get("event_type")).toList());
    }

    @Test
    void shouldNotRecordAnEventWhenTheTransitionIsRejected() {
        UUID id = register();
        patchStatus(id, "ALTA").expectStatus().isOk();

        patchStatus(id, "EN_CONSULTA").expectStatus().isEqualTo(409);

        assertEquals(2, outboxRows(id).size());
    }

    @Test
    void shouldNotRecordAnythingWhenValidationFails() {
        long before = outboxCount();

        client.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(INVALID_PATIENT)
                .exchange()
                .expectStatus().isBadRequest();

        assertEquals(before, outboxCount());
    }

    // ---------- helpers ----------

    private UUID register() {
        PatientResponse response = client.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CRITICAL_PATIENT)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PatientResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        return response.id();
    }

    private WebTestClient.ResponseSpec patchStatus(UUID id, String status) {
        return client.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"estado\":\"%s\"}".formatted(status))
                .exchange();
    }

    private List<Map<String, Object>> outboxRows(UUID patientId) {
        return databaseClient.sql("SELECT * FROM outbox_event WHERE patient_id = :id ORDER BY seq")
                .bind("id", patientId)
                .fetch()
                .all()
                .collectList()
                .block();
    }

    private long outboxCount() {
        return databaseClient.sql("SELECT COUNT(*) FROM outbox_event")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
    }
}