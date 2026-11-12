package com.vida_cruz.triage_service.infraestructure.entrypoints.validation;

import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.RegisterPatientRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DiastolicLowerThanSystolicValidator
        implements ConstraintValidator<DiastolicLowerThanSystolic, RegisterPatientRequest> {

    @Override
    public boolean isValid(RegisterPatientRequest request, ConstraintValidatorContext context) {
        if (validateNull(request)) {
            return true;
        }
        if (request.diastolicBp() < request.systolicBp()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("diastolicBp")
                .addConstraintViolation();
        return false;
    }

    private static boolean validateNull(RegisterPatientRequest request) {
        return request == null || request.systolicBp() == null || request.diastolicBp() == null;
    }
}