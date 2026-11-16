package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record EscalationAlert(
        UUID patientId,
        TriageLevel level,
        int slaMinutes,
        Instant registeredAt,
        Instant escalatedAt,
        int attempts
) {

    public static EscalationAlert of(Patient escalated) {
        return new EscalationAlert(escalated.id(), escalated.triageLevel(), escalated.slaMinutes(),
                escalated.registeredAt(), escalated.escalatedAt(), 0);
    }

    public long waitedMinutes() {
        return Duration.between(registeredAt, escalatedAt).toMinutes();
    }

    /** Only the patient id and the level: the alert leaves for an external channel, so it carries no clinical or personal data. */
    public String message() {
        return "Paciente %s escalado: nivel %s. Superó su SLA de %d min sin entrar a consulta (espera: %d min)."
                .formatted(patientId, level, slaMinutes, waitedMinutes());
    }
}