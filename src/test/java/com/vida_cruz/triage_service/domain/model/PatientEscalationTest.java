package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientEscalationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant DEADLINE = REGISTERED_AT.plus(Duration.ofMinutes(5));

    @Test
    void shouldComputeTheSlaDeadlineFromRegistrationTime() {
        assertEquals(DEADLINE, patient(PatientStatus.REGISTRADO, false).slaDeadline());
    }

    @Test
    void shouldNotEscalateExactlyAtTheDeadline() {
        assertFalse(patient(PatientStatus.REGISTRADO, false).canBeEscalated(DEADLINE));
    }

    @Test
    void shouldEscalateAsSoonAsTheDeadlineIsExceeded() {
        assertTrue(patient(PatientStatus.REGISTRADO, false).canBeEscalated(DEADLINE.plusNanos(1)));
    }

    @Test
    void shouldNotEscalateWithinTheSla() {
        assertFalse(patient(PatientStatus.REGISTRADO, false).canBeEscalated(DEADLINE.minusSeconds(1)));
    }

    @Test
    void shouldReturnAnEscalatedCopyAndLeaveTheOriginalUntouched() {
        Patient waiting = patient(PatientStatus.REGISTRADO, false);
        Instant now = DEADLINE.plusSeconds(30);

        Patient escalated = waiting.escalate(now).orElseThrow();

        assertTrue(escalated.escalated());
        assertEquals(now, escalated.escalatedAt());
        assertEquals(waiting.toBuilder().escalated(true).escalatedAt(now).build(), escalated,
                "only the escalation fields change");
        assertFalse(waiting.escalated());
    }

    @ParameterizedTest(name = "a patient in {0} is never escalated")
    @EnumSource(value = PatientStatus.class, names = {"EN_CONSULTA", "ALTA"})
    void shouldNeverEscalateAPatientOutsideTheWaitingState(PatientStatus status) {
        assertTrue(patient(status, false).escalate(DEADLINE.plusSeconds(3600)).isEmpty());
    }

    @Test
    void shouldNotEscalateTwice() {
        Patient alreadyEscalated = patient(PatientStatus.REGISTRADO, true);

        assertTrue(alreadyEscalated.escalate(DEADLINE.plusSeconds(3600)).isEmpty());
    }

    @Test
    void shouldKeepTheEscalationFlagWhenThePatientEntersConsultation() {
        Patient escalated = patient(PatientStatus.REGISTRADO, false).escalate(DEADLINE.plusSeconds(10)).orElseThrow();

        Patient inConsultation = escalated.transitionTo(PatientStatus.EN_CONSULTA, DEADLINE.plusSeconds(60));

        assertTrue(inConsultation.escalated());
        assertEquals(escalated.escalatedAt(), inConsultation.escalatedAt());
    }

    @Test
    void shouldBuildTheEscalationAuditEntry() {
        Patient escalated = patient(PatientStatus.REGISTRADO, false).escalate(DEADLINE.plusSeconds(10)).orElseThrow();

        PatientAudit audit = PatientAudit.escalation(escalated);

        assertEquals(escalated.id(), audit.patientId());
        assertEquals(PatientStatus.REGISTRADO, audit.previousStatus());
        assertEquals(PatientStatus.REGISTRADO, audit.newStatus());
        assertEquals(AuditEventType.ESCALAMIENTO, audit.eventType());
        assertEquals(escalated.escalatedAt(), audit.occurredAt());
    }

    @Test
    void shouldBuildTheEscalationEventAsDescribedInTheContract() {
        Patient escalated = patient(PatientStatus.REGISTRADO, false).escalate(DEADLINE.plusSeconds(10)).orElseThrow();

        TriageEvent event = TriageEvent.escalation(escalated);

        assertEquals(TriageEventType.PATIENT_ESCALATED, event.type());
        assertEquals(escalated.id(), event.patientId());
        assertEquals(escalated.escalatedAt(), event.occurredAt());
        assertEquals(TriageLevel.CRITICO, event.level());
        assertEquals(PatientStatus.REGISTRADO, event.status());
        assertEquals(PatientStatus.REGISTRADO, event.previousStatus());
        assertEquals(5, event.slaMinutes());
        assertNotEquals(event.eventId(), TriageEvent.escalation(escalated).eventId());
    }

    private static Patient patient(PatientStatus status, boolean escalated) {
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 36.8, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(status)
                .escalated(escalated)
                .escalatedAt(escalated ? REGISTERED_AT.plusSeconds(400) : null)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}