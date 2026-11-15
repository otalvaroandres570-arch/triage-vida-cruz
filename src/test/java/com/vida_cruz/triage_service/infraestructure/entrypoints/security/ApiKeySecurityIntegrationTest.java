package com.vida_cruz.triage_service.infraestructure.entrypoints.security;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestPropertySource(properties = {
        "triage.security.enabled=true",
        "triage.security.api-keys=test-api-key-0123456789"})
class ApiKeySecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "test-api-key-0123456789";

    @Autowired
    private WebTestClient client;

    @Test
    void shouldKeepTheHealthProbeOpen() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().value(status -> assertNotEquals(401, status.intValue()));
    }

    @Test
    void shouldRejectRequestsWithoutAKeyOrWithAWrongOne() {
        client.get().uri("/triage/pacientes/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/triage/pacientes/{id}", UUID.randomUUID())
                .header("X-API-Key", "wrong-key-0123456789").exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/triage/pacientes").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldServeTheRequestsWithAValidKey() {
        client.get().uri("/triage/pacientes/{id}", UUID.randomUUID())
                .header("X-API-Key", VALID_KEY).exchange()
                .expectStatus().isNotFound();
    }
}