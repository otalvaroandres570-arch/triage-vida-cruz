package com.vida_cruz.triage_service.domain.model.enums;

public enum AuditEventType {
    REGISTRO,
    ENTRADA_A_CONSULTA,
    ESCALAMIENTO,
    ALTA;

    public static AuditEventType forStatus(PatientStatus status) {
        return switch (status) {
            case REGISTRADO -> REGISTRO;
            case EN_CONSULTA -> ENTRADA_A_CONSULTA;
            case ALTA -> ALTA;
        };
    }
}