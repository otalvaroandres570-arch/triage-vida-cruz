package com.vida_cruz.triage_service.infraestructure.adapters.alerting;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.SyntheticPastData;
import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.RetryBackoff;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.usecase.DispatchAlertsUseCase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertDeliveryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("1996-01-01T00:00:00Z");
    private static final Instant NOW = REGISTERED_AT.plus(Duration.ofMinutes(10));

    @Autowired
    private AlertQueue queue;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DatabaseClient databaseClient;

    private MockWebServer server;

    @BeforeEach
    void startChannel() throws IOException {
        SyntheticPastData.clean(databaseClient);
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopChannel() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldKeepRetryingUntilTheChannelRecoversAndThenDeliverExactlyOnce() {
        EscalationAlert alert = newAlert();
        queue.enqueue(alert, NOW).block();
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200));

        assertEquals(0, dispatcherAt(NOW).dispatchDue().block(), "the first attempt fails");
        Map<String, Object> afterFailure = alertRow(alert);
        assertEquals(1, ((Number) afterFailure.get("attempts")).intValue());
        assertEquals("Webhook answered HTTP 503", afterFailure.get("last_error"));
        assertNull(afterFailure.get("sent_at"), "the alert is not lost: it stays queued");

        assertEquals(1, dispatcherAt(NOW.plusSeconds(6)).dispatchDue().block(), "the retry, once its wait has passed, succeeds");
        assertNotNull(alertRow(alert).get("sent_at"));

        assertEquals(0, dispatcherAt(NOW.plus(Duration.ofHours(1))).dispatchDue().block(), "a delivered alert is never sent again");
        assertEquals(2, server.getRequestCount());
    }

    // ---------- helpers ----------

    private DispatchAlertsUseCase dispatcherAt(Instant now) {
        return new DispatchAlertsUseCase(queue,
                new WebhookAlertNotifier(WebClient.builder().build(), server.url("/hook").toString(), Duration.ofSeconds(2)),
                Clock.fixed(now, ZoneOffset.UTC), 20, Duration.ofSeconds(60),
                new RetryBackoff(Duration.ofSeconds(5), Duration.ofMinutes(5)));
    }

    private Map<String, Object> alertRow(EscalationAlert alert) {
        return databaseClient.sql("SELECT attempts, last_error, sent_at FROM escalation_alert WHERE patient_id = :id")
                .bind("id", alert.patientId())
                .fetch()
                .one()
                .block();
    }

    private EscalationAlert newAlert() {
        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente Prueba")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 36.8, 8, 15))
                .chiefComplaint("Prueba")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(PatientStatus.REGISTRADO)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
        patientRepository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
        return EscalationAlert.of(patient.escalate(NOW).orElseThrow());
    }
}