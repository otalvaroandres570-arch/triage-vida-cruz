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
}