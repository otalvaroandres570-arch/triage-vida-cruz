package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of the events published in "triaje.eventos".
 * Contract documented in vidacruz-platform/docs/events/triaje-eventos.md.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TriageEventMessage(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        UUID patientId,
        String level,
        String status,
        String previousStatus,
        int slaMinutes
) {

    public static final int CONTRACT_VERSION = 1;

    public static TriageEventMessage from(TriageEvent event) {
        return new TriageEventMessage(
                event.eventId(),
                wireName(event.type()),
                CONTRACT_VERSION,
                event.occurredAt(),
                event.patientId(),
                event.level().name(),
                event.status().name(),
                event.previousStatus() == null ? null : event.previousStatus().name(),
                event.slaMinutes());
    }

    static String wireName(TriageEventType type) {
        return switch (type) {
            case PATIENT_REGISTERED -> "paciente_registrado";
            case PATIENT_ENTERED_CONSULTATION -> "paciente_en_consulta";
            case PATIENT_DISCHARGED -> "paciente_alta";
            case PATIENT_ESCALATED -> "paciente_escalado";
        };
    }
}