package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.time.Instant;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        @JsonProperty("nivel") TriageLevel level,
        @JsonProperty("slaMinutos") int slaMinutes,
        @JsonProperty("estado") PatientStatus status,
        @JsonProperty("fechaRegistro") Instant registeredAt
) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.id(),
                patient.triageLevel(),
                patient.slaMinutes(),
                patient.status(),
                patient.registeredAt());
    }
}