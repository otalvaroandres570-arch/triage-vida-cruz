package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vida_cruz.triage_service.domain.model.PatientDetails;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatientDetailResponse(
        UUID id,
        String fullName,
        int age,
        Vitals vitals,
        String chiefComplaint,
        @JsonProperty("nivel") TriageLevel level,
        @JsonProperty("slaMinutos") int slaMinutes,
        @JsonProperty("estado") PatientStatus status,
        @JsonProperty("escalado") boolean escalated,
        @JsonProperty("fechaEscalamiento") Instant escalatedAt,
        @JsonProperty("fechaRegistro") Instant registeredAt,
        @JsonProperty("fechaCambioEstado") Instant statusChangedAt,
        @JsonProperty("fechaInicioConsulta") Instant consultationStartedAt,
        @JsonProperty("tiempoEnEstadoSegundos") long secondsInCurrentStatus
) {

    public record Vitals(int heartRate, int systolicBp, int diastolicBp, int spo2,
                         double temperature, int pain, int gcs) {

        static Vitals from(VitalSigns vitals) {
            return new Vitals(vitals.heartRate(), vitals.systolicBp(), vitals.diastolicBp(),
                    vitals.spo2(), vitals.temperature(), vitals.pain(), vitals.gcs());
        }
    }

    public static PatientDetailResponse from(PatientDetails details) {
        var patient = details.patient();
        return new PatientDetailResponse(
                patient.id(),
                patient.fullName(),
                patient.age(),
                Vitals.from(patient.vitals()),
                patient.chiefComplaint(),
                patient.triageLevel(),
                patient.slaMinutes(),
                patient.status(),
                patient.escalated(),
                patient.escalatedAt(),
                patient.registeredAt(),
                patient.statusChangedAt(),
                patient.consultationStartedAt(),
                details.timeInCurrentStatus().toSeconds());
    }
}