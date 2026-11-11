package com.vida_cruz.triage_service.infraestructure.entrypoints.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(Instant.now(), status, code, message, null);
    }

    public static ErrorResponse of(int status, String code, String message, List<FieldViolation> violations) {
        return new ErrorResponse(Instant.now(), status, code, message, violations);
    }
}
