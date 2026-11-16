package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@TestPropertySource(properties = {"triage.sla.critico=1", "triage.sla.no-urgente2=7"})
class SlaConfigurationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void shouldApplyConfiguredSlaToCriticalPatients() {
        register(130, 180, 110, 97, 36.8, 8, 15, "Dolor torácico")
                .expectBody()
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.slaMinutos").isEqualTo(1);
    }

    @Test
    void shouldApplyConfiguredSlaToLowestPriorityPatients() {
        register(70, 115, 75, 99, 36.4, 0, 15, "Renovación de recetario")
                .expectBody()
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE_2")
                .jsonPath("$.slaMinutos").isEqualTo(7);
    }

    @Test
    void shouldKeepDefaultsForLevelsThatWereNotConfigured() {
        register(72, 120, 80, 99, 36.5, 2, 15, "Herida por cortar")
                .expectBody()
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.slaMinutos").isEqualTo(60);
    }

    private WebTestClient.ResponseSpec register(int heartRate, int systolic, int diastolic, int spo2,
                                                double temperature, int pain, int gcs, String complaint) {
        String json = """
                {"fullName":"Juan Pérez","age":40,"heartRate":%s,"systolicBp":%s,"diastolicBp":%s,
                 "spo2":%s,"temperature":%s,"pain":%s,"gcs":%s,"chiefComplaint":"%s"}
                """.formatted(heartRate, systolic, diastolic, spo2, temperature, pain, gcs, complaint);

        return client.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isCreated();
    }
}