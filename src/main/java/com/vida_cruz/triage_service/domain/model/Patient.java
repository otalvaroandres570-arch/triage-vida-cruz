package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Patient(
        UUID id,
        String fullName,
        int age,
        VitalSigns vitals,
        String chiefComplaint,
        TriageLevel triageLevel,
        int slaMinutes,
        PatientStatus status,
        boolean escalated,
        Instant escalatedAt,
        Instant registeredAt,
        Instant statusChangedAt,
        Instant consultationStartedAt
) {
}