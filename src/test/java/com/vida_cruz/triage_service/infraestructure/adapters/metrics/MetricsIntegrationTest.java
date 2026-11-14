package com.vida_cruz.triage_service.infraestructure.adapters.metrics;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CRITICAL_PATIENT = """
            {"fullName":"Juan Pérez","age":40,"heartRate":130,"systolicBp":180,"diastolicBp":110,
             "spo2":97,"temperature":36.8,"pain":8,"gcs":15,"chiefComplaint":"Dolor torácico"}
            """;

    @Autowired
    private WebTestClient client;

    @Autowired
    private MeterRegistry registry;

    @Test
    void shouldCountEachCommittedRegistrationUnderItsLevelAndIgnoreTheRejectedOnes() {
        double before = criticalClassifications();

        post(CRITICAL_PATIENT).expectStatus().isCreated();
        post(CRITICAL_PATIENT.replace("\"spo2\":97", "\"spo2\":101")).expectStatus().isBadRequest();

        assertEquals(before + 1, criticalClassifications());
    }

    private double criticalClassifications() {
        return registry.get("triage.classifications").tag("level", "CRITICO").counter().count();
    }

    private WebTestClient.ResponseSpec post(String json) {
        return client.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();
    }
}