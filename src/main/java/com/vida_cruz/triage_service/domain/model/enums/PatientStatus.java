package com.vida_cruz.triage_service.domain.model.enums;

public enum PatientStatus {
    REGISTRADO,
    EN_CONSULTA,
    ALTA;

    public boolean canTransitionTo(PatientStatus target) {
        return switch (this) {
            case REGISTRADO -> target == EN_CONSULTA || target == ALTA;
            case EN_CONSULTA -> target == ALTA;
            case ALTA -> false;
        };
    }
}