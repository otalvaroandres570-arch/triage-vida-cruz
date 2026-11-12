package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldRegisterCriticalPatientAndPersistItWithItsAudit() {
        PatientResponse response = post(body(130, 180, 110, 97, 36.8, 8, 15, "Dolor torácico"))
                .expectStatus().isCreated()
                .expectBody(PatientResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(TriageLevel.CRITICO, response.level());
        assertEquals(5, response.slaMinutes());
        assertEquals(PatientStatus.REGISTRADO, response.status());
        assertNotNull(response.registeredAt());

        StepVerifier.create(databaseClient.sql("""
                                SELECT triage_level, sla_minutes, status FROM patient WHERE id = :id
                                """)
                        .bind("id", response.id())
                        .fetch()
                        .one())
                .assertNext(row -> {
                    assertEquals(1, ((Number) row.get("triage_level")).intValue());
                    assertEquals(5, ((Number) row.get("sla_minutes")).intValue());
                    assertEquals("REGISTRADO", row.get("status"));
                })
                .verifyComplete();

        StepVerifier.create(databaseClient.sql("SELECT COUNT(*) FROM patient_audit WHERE patient_id = :id")
                        .bind("id", response.id())
                        .map(row -> row.get(0, Long.class))
                        .one())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void shouldExposeTheResponseFieldsRequiredByTheSpec() {
        post(body(72, 120, 80, 99, 36.5, 2, 15, "Herida por cortar"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.slaMinutos").isEqualTo(60)
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.fechaRegistro").value(value -> Instant.parse(value.toString()));
    }

    @ParameterizedTest(name = "{0} -> {9}")
    @CsvSource(delimiter = '|', value = {
            "E1|130|180|110|97|36.8|8|15|Dolor torácico|CRITICO",
            "E2|95|150|95|91|37.0|4|15|Dificultad para respirar|URGENTE",
            "E3|88|130|85|98|38.2|6|15|Fiebre|URGENTE_2",
            "E4|72|120|80|99|36.5|2|15|Herida por cortar|NO_URGENTE",
            "E5|70|115|75|99|36.4|0|15|Renovación de recetario|NO_URGENTE_2",
            "E6|35|80|50|85|35.9|0|6|Inconsciente|CRITICO"
    })
    void shouldClassifyReferencePatientsThroughTheApi(String caseId, int heartRate, int systolic,
                                                              int diastolic, int spo2, double temperature,
                                                              int pain, int gcs, String complaint, String expectedLevel) {
        post(body(heartRate, systolic, diastolic, spo2, temperature, pain, gcs, complaint))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.nivel").isEqualTo(expectedLevel);
    }

    @Test
    void shouldRejectOutOfRangeValueNamingTheInvalidField() {
        post(body(72, 120, 80, 101, 36.5, 2, 15, "Herida por cortar"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.violations[0].field").isEqualTo("spo2")
                .jsonPath("$.message").value(message -> assertTrue(message.toString().contains("spo2")));
    }

    @Test
    void shouldRejectDiastolicNotLowerThanSystolic() {
        post(body(72, 100, 100, 99, 36.5, 2, 15, "Herida por cortar"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.violations[0].field").isEqualTo("diastolicBp");
    }

    @Test
    void shouldRejectMissingField() {
        String withoutComplaint = """
                {"fullName":"Juan Pérez","age":40,"heartRate":72,"systolicBp":120,"diastolicBp":80,
                 "spo2":99,"temperature":36.5,"pain":2,"gcs":15}
                """;

        post(withoutComplaint)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.violations[0].field").isEqualTo("chiefComplaint");
    }

    @Test
    void shouldRejectMalformedJson() {
        post("{ not json")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void shouldNotPersistAnythingWhenValidationFails() {
        Long before = countPatients();

        post(body(72, 120, 80, 101, 36.5, 2, 15, "Herida por cortar"))
                .expectStatus().isBadRequest();

        assertEquals(before, countPatients());
    }

    private WebTestClient.ResponseSpec post(String json) {
        return client.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();
    }

    private Long countPatients() {
        return databaseClient.sql("SELECT COUNT(*) FROM patient")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
    }

    private static String body(int heartRate, int systolic, int diastolic, int spo2,
                               double temperature, int pain, int gcs, String complaint) {
        return """
                {"fullName":"Juan Pérez","age":40,"heartRate":%s,"systolicBp":%s,"diastolicBp":%s,
                 "spo2":%s,"temperature":%s,"pain":%s,"gcs":%s,"chiefComplaint":"%s"}
                """.formatted(heartRate, systolic, diastolic, spo2, temperature, pain, gcs, complaint);
    }
}