package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientTransitionTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "REGISTRADO,EN_CONSULTA",
            "REGISTRADO,ALTA",
            "EN_CONSULTA,ALTA"
    })
    void shouldAllowValidTransitions(PatientStatus from, PatientStatus to) {
        Patient patient = patientIn(from);

        Patient updated = patient.transitionTo(to, LATER);

        assertEquals(to, updated.status());
        assertEquals(LATER, updated.statusChangedAt());
        assertEquals(from, patient.status(), "the original patient must not be modified");
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "ALTA,EN_CONSULTA",
            "ALTA,REGISTRADO",
            "ALTA,ALTA",
            "EN_CONSULTA,REGISTRADO",
            "EN_CONSULTA,EN_CONSULTA",
            "REGISTRADO,REGISTRADO"
    })
    void shouldRejectInvalidTransitions(PatientStatus from, PatientStatus to) {
        Patient patient = patientIn(from);

        InvalidStatusTransitionException error =
                assertThrows(InvalidStatusTransitionException.class, () -> patient.transitionTo(to, LATER));

        assertTrue(error.getMessage().contains(from.name()));
        assertTrue(error.getMessage().contains(to.name()));
    }

    @Test
    void shouldRecordFirstConsultationTimeWhenEnteringConsultation() {
        Patient updated = patientIn(PatientStatus.REGISTRADO).transitionTo(PatientStatus.EN_CONSULTA, LATER);

        assertEquals(LATER, updated.consultationStartedAt());
    }

    @Test
    void shouldLeaveConsultationTimeEmptyOnDirectDischarge() {
        Patient updated = patientIn(PatientStatus.REGISTRADO).transitionTo(PatientStatus.ALTA, LATER);

        assertNull(updated.consultationStartedAt());
    }

    @Test
    void shouldKeepConsultationTimeWhenDischargedAfterConsultation() {
        Instant dischargedAt = LATER.plusSeconds(900);
        Patient inConsultation = patientIn(PatientStatus.REGISTRADO).transitionTo(PatientStatus.EN_CONSULTA, LATER);

        Patient discharged = inConsultation.transitionTo(PatientStatus.ALTA, dischargedAt);

        assertEquals(LATER, discharged.consultationStartedAt());
        assertEquals(dischargedAt, discharged.statusChangedAt());
    }

    @Test
    void shouldKeepEscalationDataAcrossTransitions() {
        Instant escalatedAt = REGISTERED_AT.plusSeconds(300);
        Patient escalated = patientIn(PatientStatus.REGISTRADO).toBuilder()
                .escalated(true)
                .escalatedAt(escalatedAt)
                .build();

        Patient updated = escalated.transitionTo(PatientStatus.EN_CONSULTA, LATER);

        assertTrue(updated.escalated());
        assertEquals(escalatedAt, updated.escalatedAt());
    }

    @ParameterizedTest(name = "{0} -> {1} is audited as {2}")
    @CsvSource({
            "REGISTRADO,EN_CONSULTA,ENTRADA_A_CONSULTA",
            "REGISTRADO,ALTA,ALTA",
            "EN_CONSULTA,ALTA,ALTA"
    })
    void shouldBuildAuditEntryForStatusChange(PatientStatus from, PatientStatus to, AuditEventType expectedEvent) {
        Patient before = patientIn(from);
        Patient after = before.transitionTo(to, LATER);

        PatientAudit audit = PatientAudit.statusChange(before, after);

        assertEquals(before.id(), audit.patientId());
        assertEquals(from, audit.previousStatus());
        assertEquals(to, audit.newStatus());
        assertEquals(expectedEvent, audit.eventType());
        assertEquals(LATER, audit.occurredAt());
    }

    private static Patient patientIn(PatientStatus status) {
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 36.8, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(status)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}