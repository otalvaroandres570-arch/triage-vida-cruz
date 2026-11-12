package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;

import java.time.Instant;
import java.util.UUID;

final class TestPatients {

    private TestPatients() {
    }

    static Patient in(PatientStatus status, Instant at) {
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 36.8, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(status)
                .escalated(false)
                .registeredAt(at)
                .statusChangedAt(at)
                .build();
    }
}