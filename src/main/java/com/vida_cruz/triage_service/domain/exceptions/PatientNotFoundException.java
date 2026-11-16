package com.vida_cruz.triage_service.domain.exceptions;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(UUID patientId) {
        super("Patient not found: " + patientId);
    }
}
