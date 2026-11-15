package com.vida_cruz.triage_service.infraestructure.entrypoints.health;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthProbesIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HealthEndpointGroups groups;

    @Autowired
    private WebTestClient client;

    @Test
    void shouldMakeReadinessDependOnTheDatabaseButNotOnKafkaAndLivenessOnNothingExternal() {
        assertTrue(groups.get("readiness").isMember("readinessState"));
        assertTrue(groups.get("readiness").isMember("r2dbc"));
        assertFalse(groups.get("readiness").isMember("kafka"));

        assertTrue(groups.get("liveness").isMember("livenessState"));
        assertFalse(groups.get("liveness").isMember("r2dbc"));
    }

    @Test
    void shouldAnswerUpOnBothProbes() {
        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}