package com.vida_cruz.triage_service.domain.model;

public record RegisterPatientCommand(
        String fullName,
        int age,
        VitalSigns vitals,
        String chiefComplaint
) {
}