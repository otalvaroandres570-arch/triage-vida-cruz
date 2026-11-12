package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.time.Instant;
import java.util.UUID;

public record TriageEvent(
        UUID eventId,
        TriageEventType type,
        Instant occurredAt,
        UUID patientId,
        TriageLevel level,
        PatientStatus status,
        PatientStatus previousStatus,
        int slaMinutes
) {

    public static TriageEvent registration(Patient patient) {
        return new TriageEvent(
                UUID.randomUUID(),
                TriageEventType.PATIENT_REGISTERED,
                patient.registeredAt(),
                patient.id(),
                patient.triageLevel(),
                patient.status(),
                null,
                patient.slaMinutes());
    }

    public static TriageEvent statusChange(Patient before, Patient after) {
        TriageEventType type = switch (after.status()) {
            case EN_CONSULTA -> TriageEventType.PATIENT_ENTERED_CONSULTATION;
            case ALTA -> TriageEventType.PATIENT_DISCHARGED;
            case REGISTRADO -> throw new IllegalArgumentException("A patient cannot move back to REGISTRADO");
        };
        return new TriageEvent(
                UUID.randomUUID(),
                type,
                after.statusChangedAt(),
                after.id(),
                after.triageLevel(),
                after.status(),
                before.status(),
                after.slaMinutes());
    }
}