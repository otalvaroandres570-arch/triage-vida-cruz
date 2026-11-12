package com.vida_cruz.triage_service.domain.exceptions;

import java.util.UUID;

public class ConcurrentPatientUpdateException extends RuntimeException {
    public ConcurrentPatientUpdateException(UUID patientId) {
        super("El estado del paciente %s cambió mientras se procesaba la solicitud. Consulte el estado actual e intente de nuevo"
                .formatted(patientId));
    }
}
