package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.AuditEventType;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;

import java.time.Instant;
import java.util.UUID;

public record PatientAudit(
        UUID patientId,
        PatientStatus previousStatus,
        PatientStatus newStatus,
        AuditEventType eventType,
        Instant occurredAt
) {

    public static PatientAudit registration(Patient patient) {
        return new PatientAudit(
                patient.id(),
                null,
                PatientStatus.REGISTRADO,
                AuditEventType.REGISTRO,
                patient.registeredAt());
    }

    public static PatientAudit statusChange(Patient before, Patient after) {
        return new PatientAudit(
                after.id(),
                before.status(),
                after.status(),
                AuditEventType.forStatus(after.status()),
                after.statusChangedAt());
    }

    /** Escalation does not change the status, so previous and new status are the same. */
    public static PatientAudit escalation(Patient escalated) {
        return new PatientAudit(
                escalated.id(),
                escalated.status(),
                escalated.status(),
                AuditEventType.ESCALAMIENTO,
                escalated.escalatedAt());
    }
}