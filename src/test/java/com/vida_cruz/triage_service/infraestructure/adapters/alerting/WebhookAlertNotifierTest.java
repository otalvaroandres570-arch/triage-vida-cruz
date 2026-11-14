package com.vida_cruz.triage_service.infraestructure.adapters.alerting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookAlertNotifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldPostAChatCompatibleMessageWithoutClinicalOrPersonalData() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        EscalationAlert alert = alert();

        StepVerifier.create(notifier(Duration.ofSeconds(2)).notify(alert)).verifyComplete();

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/hooks/secret-token", request.getPath());
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));

        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertEquals(alert.message(), body.get("text").asText());
        assertEquals(Set.of("text", "alert"), MAPPER.convertValue(body, java.util.Map.class).keySet());
        assertEquals(Set.of("type", "patientId", "level", "slaMinutes", "waitedMinutes", "escalatedAt"),
                MAPPER.convertValue(body.get("alert"), java.util.Map.class).keySet());
        assertEquals(alert.patientId().toString(), body.get("alert").get("patientId").asText());
    }

    @Test
    void shouldFailWithoutLeakingTheUrlWhenTheChannelAnswersAnError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(notifier(Duration.ofSeconds(2)).notify(alert()))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(WebhookDeliveryException.class, error);
                    assertEquals("Webhook answered HTTP 500", error.getMessage());
                })
                .verify();
    }

    @Test
    void shouldFailWhenTheChannelIsTooSlow() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeadersDelay(2, TimeUnit.SECONDS));

        StepVerifier.create(notifier(Duration.ofMillis(200)).notify(alert()))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(WebhookDeliveryException.class, error);
                    assertTrue(error.getMessage().startsWith("Webhook timed out"));
                })
                .verify(Duration.ofSeconds(5));
    }

    private WebhookAlertNotifier notifier(Duration timeout) {
        return new WebhookAlertNotifier(WebClient.builder().build(), server.url("/hooks/secret-token").toString(), timeout);
    }

    private static EscalationAlert alert() {
        return new EscalationAlert(UUID.randomUUID(), TriageLevel.CRITICO, 5,
                Instant.parse("2026-11-13T12:00:00Z"), Instant.parse("2026-11-13T12:07:00Z"), 0);
    }
}