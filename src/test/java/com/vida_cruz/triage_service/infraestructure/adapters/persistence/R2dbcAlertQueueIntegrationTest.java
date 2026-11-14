package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.SyntheticPastData;
import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcAlertQueueIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("1997-01-01T00:00:00Z");
    private static final Instant NOW = REGISTERED_AT.plus(Duration.ofMinutes(10));
    private static final Duration LEASE = Duration.ofSeconds(60);

    @Autowired
    private AlertQueue queue;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void cleanSyntheticData() {
        SyntheticPastData.clean(databaseClient);
    }

    @Test
    void shouldKeepASingleAlertPerPatient() {
        EscalationAlert alert = newAlert();

        queue.enqueue(alert, NOW).block();
        queue.enqueue(alert, NOW).block();

        assertEquals(1L, row(alert, "SELECT COUNT(*) AS value FROM escalation_alert WHERE patient_id = :id", Long.class));
    }

    @Test
    void shouldClaimOnlyDueAlertsAndHideThemWhileTheyAreLeased() {
        EscalationAlert due = newAlert();
        EscalationAlert notDueYet = newAlert();
        queue.enqueue(due, NOW).block();
        queue.enqueue(notDueYet, NOW.plusSeconds(120)).block();

        assertEquals(List.of(due), claim(NOW), "only the due alert is returned");
        assertTrue(claim(NOW).isEmpty(), "while it is leased nobody else gets it");
        assertEquals(List.of(due), claim(NOW.plus(LEASE).plusSeconds(1)), "if it is never marked, it comes back after the lease");
    }

    @Test
    void shouldNeverReturnAnAlertThatWasSent() {
        EscalationAlert alert = newAlert();
        queue.enqueue(alert, NOW).block();

        queue.markSent(alert.patientId(), NOW).block();

        assertTrue(claim(NOW.plus(Duration.ofDays(1))).isEmpty());
        assertNotNull(row(alert, "SELECT sent_at AS value FROM escalation_alert WHERE patient_id = :id", Object.class));
    }

    @Test
    void shouldRescheduleAFailedAlertKeepingItsAttemptsAndTheError() {
        EscalationAlert alert = newAlert();
        queue.enqueue(alert, NOW).block();
        claim(NOW);

        queue.markFailed(alert.patientId(), 2, NOW.plusSeconds(10), "webhook down").block();

        assertTrue(claim(NOW.plusSeconds(5)).isEmpty(), "it is not due until its retry time");
        List<EscalationAlert> retry = claim(NOW.plusSeconds(11));
        assertEquals(1, retry.size());
        assertEquals(2, retry.getFirst().attempts());
        assertEquals("webhook down",
                row(alert, "SELECT last_error AS value FROM escalation_alert WHERE patient_id = :id", String.class));
    }

    @Test
    void shouldGiveEachAlertToOnlyOneOfTwoConcurrentClaimers() {
        List<EscalationAlert> alerts = IntStream.range(0, 4).mapToObj(index -> newAlert()).toList();
        Flux.fromIterable(alerts).concatMap(alert -> queue.enqueue(alert, NOW)).then().block();

        List<EscalationAlert> claimed = Flux.merge(
                        queue.claimDue(NOW, 4, NOW.plus(LEASE)).subscribeOn(Schedulers.parallel()),
                        queue.claimDue(NOW, 4, NOW.plus(LEASE)).subscribeOn(Schedulers.parallel()))
                .collectList()
                .block();

        assertEquals(4, claimed.size());
        assertEquals(4, claimed.stream().map(EscalationAlert::patientId).collect(Collectors.toSet()).size());
    }

    // ---------- helpers ----------

    private List<EscalationAlert> claim(Instant now) {
        return queue.claimDue(now, 10, now.plus(LEASE)).collectList().block();
    }

    private <T> T row(EscalationAlert alert, String sql, Class<T> type) {
        return databaseClient.sql(sql)
                .bind("id", alert.patientId())
                .map(row -> row.get("value", type))
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