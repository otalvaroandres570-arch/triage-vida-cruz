package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientStatusEndpointsIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CRITICAL_PATIENT = """
            {"fullName":"Juan Pérez","age":40,"heartRate":130,"systolicBp":180,"diastolicBp":110,
             "spo2":97,"temperature":36.8,"pain":8,"gcs":15,"chiefComplaint":"Dolor torácico"}
            """;

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    // ---------- PATCH: valid transitions ----------

    @Test
    void shouldMoveRegisteredPatientToConsultation() {
        UUID id = registerPatient();

        patchStatus(id, "EN_CONSULTA")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.nivel").isEqualTo("CRITICO");

        assertEquals(List.of("REGISTRO", "ENTRADA_A_CONSULTA"), auditEventTypes(id));
        assertTrue(consultationStartedAtIsSet(id));
    }

    @Test
    void shouldAllowDirectDischarge() {
        UUID id = registerPatient();

        patchStatus(id, "ALTA")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ALTA");

        assertEquals(List.of("REGISTRO", "ALTA"), auditEventTypes(id));
    }

    @Test
    void shouldCompleteTheFullClinicalFlowAndAuditEveryStep() {
        UUID id = registerPatient();

        patchStatus(id, "EN_CONSULTA").expectStatus().isOk();
        patchStatus(id, "ALTA").expectStatus().isOk();

        assertEquals(List.of("REGISTRO", "ENTRADA_A_CONSULTA", "ALTA"), auditEventTypes(id));
        getPatient(id).expectBody().jsonPath("$.estado").isEqualTo("ALTA");
    }

    // ---------- PATCH: errors ----------

    @Test
    void shouldReturn409WhenTransitionIsInvalid() {
        UUID id = registerPatient();
        patchStatus(id, "ALTA").expectStatus().isOk();

        patchStatus(id, "EN_CONSULTA")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION")
                .jsonPath("$.message").value(message -> {
                    assertTrue(message.toString().contains("ALTA"));
                    assertTrue(message.toString().contains("EN_CONSULTA"));
                });

        assertEquals(List.of("REGISTRO", "ALTA"), auditEventTypes(id), "a rejected change must not be audited");
    }

    @Test
    void shouldReturn409WhenRepeatingTheSameStatus() {
        UUID id = registerPatient();

        patchStatus(id, "REGISTRADO")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @ParameterizedTest(name = "estado \"{0}\" is rejected with 400")
    @ValueSource(strings = {"HOSPITALIZADO", "alta", ""})
    void shouldReturn400ForUnknownStatus(String status) {
        UUID id = registerPatient();

        patchStatus(id, status)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.violations[0].field").isEqualTo("estado");
    }

    @Test
    void shouldListAllowedValuesWhenStatusIsUnknown() {
        UUID id = registerPatient();

        patchStatus(id, "HOSPITALIZADO")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(message ->
                        assertTrue(message.toString().contains("REGISTRADO, EN_CONSULTA, ALTA")));
    }

    @ParameterizedTest(name = "body {0} is rejected with 400")
    @ValueSource(strings = {"{}", "{\"estado\":null}"})
    void shouldReturn400WhenStatusIsMissing(String body) {
        UUID id = registerPatient();

        client.patch().uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.violations[0].field").isEqualTo("estado");
    }

    @Test
    void shouldReturn404WhenPatchingUnknownPatient() {
        patchStatus(UUID.randomUUID(), "EN_CONSULTA")
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PATIENT_NOT_FOUND");
    }

    @Test
    void shouldReturn400WhenIdIsNotAUuid() {
        patchStatus("not-a-uuid", "EN_CONSULTA")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void shouldLetOnlyOneOfTwoSimultaneousRequestsSucceed() {
        UUID id = registerPatient();

        CompletableFuture<Integer> first =
                CompletableFuture.supplyAsync(() -> statusCodeOf(patchStatus(id, "EN_CONSULTA")));
        CompletableFuture<Integer> second =
                CompletableFuture.supplyAsync(() -> statusCodeOf(patchStatus(id, "EN_CONSULTA")));
        List<Integer> codes = List.of(first.join(), second.join());

        assertEquals(1, codes.stream().filter(code -> code == 200).count(), "codes: " + codes);
        assertEquals(1, codes.stream().filter(code -> code == 409).count(), "codes: " + codes);
        assertEquals(List.of("REGISTRO", "ENTRADA_A_CONSULTA"), auditEventTypes(id));
    }

    // ---------- GET ----------

    @Test
    void shouldReturnFullPatientDetails() {
        UUID id = registerPatient();

        getPatient(id)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.fullName").isEqualTo("Juan Pérez")
                .jsonPath("$.age").isEqualTo(40)
                .jsonPath("$.vitals.heartRate").isEqualTo(130)
                .jsonPath("$.vitals.systolicBp").isEqualTo(180)
                .jsonPath("$.chiefComplaint").isEqualTo("Dolor torácico")
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.slaMinutos").isEqualTo(5)
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.escalado").isEqualTo(false)
                .jsonPath("$.fechaRegistro").isNotEmpty()
                .jsonPath("$.fechaCambioEstado").isNotEmpty()
                .jsonPath("$.tiempoEnEstadoSegundos").value(seconds ->
                        assertTrue(((Number) seconds).longValue() >= 0))
                .jsonPath("$.fechaEscalamiento").doesNotExist()
                .jsonPath("$.fechaInicioConsulta").doesNotExist();
    }

    @Test
    void shouldReflectTheStatusChangeInTheDetails() {
        UUID id = registerPatient();
        patchStatus(id, "EN_CONSULTA").expectStatus().isOk();

        getPatient(id)
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.fechaInicioConsulta").isNotEmpty();
    }

    @Test
    void shouldReportTimeInCurrentStatusBasedOnTheLastStatusChange() {
        UUID id = registerPatient();
        databaseClient.sql("UPDATE patient SET status_changed_at = now() - interval '5 minutes' WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated().block();

        getPatient(id)
                .expectBody()
                .jsonPath("$.tiempoEnEstadoSegundos").value(seconds -> {
                    long value = ((Number) seconds).longValue();
                    assertTrue(value >= 300 && value < 400, "unexpected seconds: " + value);
                });
    }

    @Test
    void shouldReturn404WhenGettingUnknownPatient() {
        getPatient(UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PATIENT_NOT_FOUND");
    }

    @Test
    void shouldReturn400WhenGettingWithInvalidId() {
        getPatient("123")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PARAMETER");
    }

    // ---------- helpers ----------

    private UUID registerPatient() {
        PatientResponse response = client.post()
                .uri("/triage/pacientes")
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

    private WebTestClient.ResponseSpec patchStatus(Object id, String status) {
        return client.patch()
                .uri("/triage/pacientes/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"estado\":\"%s\"}".formatted(status))
                .exchange();
    }

    private WebTestClient.ResponseSpec getPatient(Object id) {
        return client.get().uri("/triage/pacientes/{id}", id).exchange();
    }

    private static int statusCodeOf(WebTestClient.ResponseSpec spec) {
        return spec.returnResult(String.class).getStatus().value();
    }

    private List<String> auditEventTypes(UUID patientId) {
        return databaseClient.sql("SELECT event_type FROM patient_audit WHERE patient_id = :id ORDER BY id")
                .bind("id", patientId)
                .map(row -> row.get("event_type", String.class))
                .all()
                .collectList()
                .block();
    }

    private boolean consultationStartedAtIsSet(UUID patientId) {
        return Boolean.TRUE.equals(databaseClient
                .sql("SELECT consultation_started_at IS NOT NULL FROM patient WHERE id = :id")
                .bind("id", patientId)
                .map(row -> row.get(0, Boolean.class))
                .one()
                .block());
    }
}