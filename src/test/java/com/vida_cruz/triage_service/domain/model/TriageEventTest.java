package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriageEventTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @Test
    void shouldBuildRegistrationEventFromThePatient() {
        Patient patient = patientIn(PatientStatus.REGISTRADO);

        TriageEvent event = TriageEvent.registration(patient);

        assertNotNull(event.eventId());
        assertEquals(TriageEventType.PATIENT_REGISTERED, event.type());
        assertEquals(REGISTERED_AT, event.occurredAt());
        assertEquals(patient.id(), event.patientId());
        assertEquals(TriageLevel.CRITICO, event.level());
        assertEquals(PatientStatus.REGISTRADO, event.status());
        assertNull(event.previousStatus());
        assertEquals(5, event.slaMinutes());
    }

    @ParameterizedTest(name = "{0} -> {1} is {2}")
    @CsvSource({
            "REGISTRADO,EN_CONSULTA,PATIENT_ENTERED_CONSULTATION",
            "REGISTRADO,ALTA,PATIENT_DISCHARGED",
            "EN_CONSULTA,ALTA,PATIENT_DISCHARGED"
    })
    void shouldBuildStatusChangeEvent(PatientStatus from, PatientStatus to, TriageEventType expectedType) {
        Patient before = patientIn(from);
        Patient after = before.transitionTo(to, LATER);

        TriageEvent event = TriageEvent.statusChange(before, after);

        assertEquals(expectedType, event.type());
        assertEquals(LATER, event.occurredAt());
        assertEquals(before.id(), event.patientId());
        assertEquals(from, event.previousStatus());
        assertEquals(to, event.status());
    }

    @Test
    void shouldGenerateADifferentEventIdEachTime() {
        Patient patient = patientIn(PatientStatus.REGISTRADO);

        assertNotEquals(TriageEvent.registration(patient).eventId(), TriageEvent.registration(patient).eventId());
    }

    @Test
    void shouldRejectAStatusChangeBackToRegistered() {
        Patient before = patientIn(PatientStatus.EN_CONSULTA);
        Patient after = before.toBuilder().status(PatientStatus.REGISTRADO).build();

        assertThrows(IllegalArgumentException.class, () -> TriageEvent.statusChange(before, after));
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