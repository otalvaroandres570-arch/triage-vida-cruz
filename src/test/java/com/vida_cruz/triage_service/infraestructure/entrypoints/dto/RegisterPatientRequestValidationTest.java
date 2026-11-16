package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterPatientRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldAcceptValidRequest() {
        assertTrue(validate(validRequest().build()).isEmpty());
    }

    @ParameterizedTest(name = "{0} = {1} is rejected")
    @CsvSource({
            "age,-1", "age,121",
            "heartRate,19", "heartRate,251",
            "systolicBp,59", "systolicBp,261",
            "diastolicBp,29", "diastolicBp,161",
            "spo2,69", "spo2,101",
            "temperature,33.9", "temperature,42.1",
            "pain,-1", "pain,11",
            "gcs,2", "gcs,16"
    })
    void shouldRejectValuesOutOfRange(String field, double value) {
        Set<ConstraintViolation<RegisterPatientRequest>> violations = validate(withField(field, value));

        assertTrue(hasViolationOn(violations, field),
                "Expected a violation on '%s' but got %s".formatted(field, violations));
    }

    @ParameterizedTest(name = "{0} = {1} is accepted")
    @CsvSource({
            "age,0", "age,120",
            "heartRate,20", "heartRate,250",
            "spo2,70", "spo2,100",
            "temperature,34.0", "temperature,42.0",
            "pain,0", "pain,10",
            "gcs,3", "gcs,15"
    })
    void shouldAcceptBoundaryValues(String field, double value) {
        assertTrue(validate(withField(field, value)).isEmpty());
    }

    @ParameterizedTest(name = "systolic {0} / diastolic {1} is accepted")
    @CsvSource({"60,30", "260,160", "100,99"})
    void shouldAcceptPressureBoundaries(int systolic, int diastolic) {
        RegisterPatientRequest request = validRequest().systolicBp(systolic).diastolicBp(diastolic).build();

        assertTrue(validate(request).isEmpty());
    }

    @ParameterizedTest(name = "systolic {0} / diastolic {1} is rejected")
    @CsvSource({"100,100", "100,120"})
    void shouldRejectDiastolicNotLowerThanSystolic(int systolic, int diastolic) {
        RegisterPatientRequest request = validRequest().systolicBp(systolic).diastolicBp(diastolic).build();

        Set<ConstraintViolation<RegisterPatientRequest>> violations = validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<RegisterPatientRequest> violation = violations.iterator().next();
        assertEquals("diastolicBp", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("sistólica"));
    }

    @ParameterizedTest(name = "full name \"{0}\" is rejected")
    @CsvSource({"Al", "''", "'   '"})
    void shouldRejectInvalidFullName(String fullName) {
        RegisterPatientRequest request = validRequest().fullName(fullName).build();

        assertTrue(hasViolationOn(validate(request), "fullName"));
    }

    @Test
    void shouldRejectFullNameLongerThan100Characters() {
        RegisterPatientRequest request = validRequest().fullName("A".repeat(101)).build();

        assertTrue(hasViolationOn(validate(request), "fullName"));
    }

    @Test
    void shouldRejectBlankChiefComplaint() {
        RegisterPatientRequest request = validRequest().chiefComplaint("  ").build();

        assertTrue(hasViolationOn(validate(request), "chiefComplaint"));
    }

    @Test
    void shouldReportEveryMissingField() {
        Set<ConstraintViolation<RegisterPatientRequest>> violations =
                validate(RegisterPatientRequest.builder().build());

        assertEquals(10, violations.size());
    }

    private static RegisterPatientRequest.RegisterPatientRequestBuilder validRequest() {
        return RegisterPatientRequest.builder()
                .fullName("Juan Pérez")
                .age(40)
                .heartRate(72)
                .systolicBp(120)
                .diastolicBp(80)
                .spo2(99)
                .temperature(36.5)
                .pain(2)
                .gcs(15)
                .chiefComplaint("Herida por cortar");
    }

    private static RegisterPatientRequest withField(String field, double value) {
        RegisterPatientRequest.RegisterPatientRequestBuilder builder = validRequest();
        switch (field) {
            case "age" -> builder.age((int) value);
            case "heartRate" -> builder.heartRate((int) value);
            case "systolicBp" -> builder.systolicBp((int) value);
            case "diastolicBp" -> builder.diastolicBp((int) value);
            case "spo2" -> builder.spo2((int) value);
            case "temperature" -> builder.temperature(value);
            case "pain" -> builder.pain((int) value);
            case "gcs" -> builder.gcs((int) value);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }
        return builder.build();
    }

    private static Set<ConstraintViolation<RegisterPatientRequest>> validate(RegisterPatientRequest request) {
        return validator.validate(request);
    }

    private static boolean hasViolationOn(Set<ConstraintViolation<RegisterPatientRequest>> violations,
                                          String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }
}