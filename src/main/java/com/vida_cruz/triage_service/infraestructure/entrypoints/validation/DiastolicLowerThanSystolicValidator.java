package com.vida_cruz.triage_service.infraestructure.entrypoints.validation;

import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.RegisterPatientRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Optional;

public class DiastolicLowerThanSystolicValidator
        implements ConstraintValidator<DiastolicLowerThanSystolic, RegisterPatientRequest> {

    /**
     * Missing pressures are left to @NotNull, so they count as valid here.
     * Bean Validation is a synchronous API that reports through the context, so that call stays a side effect.
     */
    @Override
    public boolean isValid(RegisterPatientRequest request, ConstraintValidatorContext context) {
        return Optional.ofNullable(request)
                .filter(r -> r.systolicBp() != null && r.diastolicBp() != null)
                .filter(r -> r.diastolicBp() >= r.systolicBp())
                .map(invalid -> reportOnDiastolic(context))
                .orElse(true);
    }

    private static boolean reportOnDiastolic(ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("diastolicBp")
                .addConstraintViolation();
        return false;
    }
}
