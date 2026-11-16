package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import com.vida_cruz.triage_service.infraestructure.entrypoints.validation.DiastolicLowerThanSystolic;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.VitalSigns;

@Builder
@DiastolicLowerThanSystolic
public record RegisterPatientRequest(

        @NotBlank(message = "es obligatorio")
        @Size(min = 3, max = 100, message = "debe tener entre 3 y 100 caracteres")
        String fullName,

        @NotNull(message = "es obligatorio")
        @Min(value = 0, message = "debe estar entre 0 y 120")
        @Max(value = 120, message = "debe estar entre 0 y 120")
        Integer age,

        @NotNull(message = "es obligatorio")
        @Min(value = 20, message = "debe estar entre 20 y 250")
        @Max(value = 250, message = "debe estar entre 20 y 250")
        Integer heartRate,

        @NotNull(message = "es obligatorio")
        @Min(value = 60, message = "debe estar entre 60 y 260")
        @Max(value = 260, message = "debe estar entre 60 y 260")
        Integer systolicBp,

        @NotNull(message = "es obligatorio")
        @Min(value = 30, message = "debe estar entre 30 y 160")
        @Max(value = 160, message = "debe estar entre 30 y 160")
        Integer diastolicBp,

        @NotNull(message = "es obligatorio")
        @Min(value = 70, message = "debe estar entre 70 y 100")
        @Max(value = 100, message = "debe estar entre 70 y 100")
        Integer spo2,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "34.0", message = "debe estar entre 34.0 y 42.0")
        @DecimalMax(value = "42.0", message = "debe estar entre 34.0 y 42.0")
        Double temperature,

        @NotNull(message = "es obligatorio")
        @Min(value = 0, message = "debe estar entre 0 y 10")
        @Max(value = 10, message = "debe estar entre 0 y 10")
        Integer pain,

        @NotNull(message = "es obligatorio")
        @Min(value = 3, message = "debe estar entre 3 y 15")
        @Max(value = 15, message = "debe estar entre 3 y 15")
        Integer gcs,

        @NotBlank(message = "es obligatorio")
        @Size(max = 500, message = "debe tener máximo 500 caracteres")
        String chiefComplaint
)  {

        public RegisterPatientCommand toCommand() {
                return new RegisterPatientCommand(
                        fullName,
                        age,
                        new VitalSigns(heartRate, systolicBp, diastolicBp, spo2, temperature, pain, gcs),
                        chiefComplaint);
        }
}