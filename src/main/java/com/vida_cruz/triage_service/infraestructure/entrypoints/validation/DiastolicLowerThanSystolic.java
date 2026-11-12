package com.vida_cruz.triage_service.infraestructure.entrypoints.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DiastolicLowerThanSystolicValidator.class)
public @interface DiastolicLowerThanSystolic {

    String message() default "debe ser menor que la presión sistólica";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}