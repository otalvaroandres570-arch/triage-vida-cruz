package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
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

        throwInvalidStatusException(target);

        PatientBuilder updated = toBuilder()
                .status(target)
                .statusChangedAt(now);

        updateConsultationStarted(target, now, updated);
        return updated.build();
    }

    private static void updateConsultationStarted(PatientStatus target, Instant now, PatientBuilder updated) {
        if (target == PatientStatus.EN_CONSULTA) {
            updated.consultationStartedAt(now);
        }
    }

    private void throwInvalidStatusException(PatientStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status.name(), target.name());
        }
    }
}