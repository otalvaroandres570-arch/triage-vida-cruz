package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import lombok.Builder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * Returns a copy of this patient in the target status, or throws if the transition is not allowed.
     * The original instance is never modified.
     */
    public Patient transitionTo(PatientStatus target, Instant now) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return Optional.of(target)
                .filter(status::canTransitionTo)
                .map(allowed -> toBuilder()
                        .status(allowed)
                        .statusChangedAt(now)
                        .consultationStartedAt(allowed == PatientStatus.EN_CONSULTA ? now : consultationStartedAt)
                        .build())
                .orElseThrow(() -> new InvalidStatusTransitionException(status.name(), target.name()));
    }

    /** The moment after which the patient has exceeded the SLA assigned at registration. */
    public Instant slaDeadline() {
        return registeredAt.plus(Duration.ofMinutes(slaMinutes));
    }

    /** Only a patient still waiting (REGISTRADO), not escalated yet, and strictly past the SLA can be escalated. */
    public boolean canBeEscalated(Instant now) {
        return status == PatientStatus.REGISTRADO && !escalated && now.isAfter(slaDeadline());
    }

    /** The escalated copy of this patient, or empty if it cannot be escalated (RN-03). */
    public Optional<Patient> escalate(Instant now) {
        return Optional.of(this)
                .filter(patient -> patient.canBeEscalated(now))
                .map(patient -> patient.toBuilder().escalated(true).escalatedAt(now).build());
    }
}