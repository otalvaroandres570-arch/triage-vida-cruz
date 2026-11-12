package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "REGISTRADO|EN_CONSULTA|ALTA",
                message = "debe ser uno de: REGISTRADO, EN_CONSULTA, ALTA")
        String estado
) {

    public PatientStatus toStatus() {
        return PatientStatus.valueOf(estado);
    }
}