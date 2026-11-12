package com.vida_cruz.triage_service.infraestructure.entrypoints.error;

import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import com.vida_cruz.triage_service.domain.exceptions.ConcurrentPatientUpdateException;
import org.springframework.beans.TypeMismatchException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        List<ErrorResponse.FieldViolation> violations = Stream.concat(
                        ex.getFieldErrors().stream()
                                .map(error -> new ErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage())),
                        ex.getGlobalErrors().stream()
                                .map(error -> new ErrorResponse.FieldViolation("request", error.getDefaultMessage())))
                .toList();

        String message = violations.stream()
                .findFirst()
                .map(first -> "Field '%s' invalid: %s".formatted(first.field(), first.message()))
                .orElse("Invalid request");

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, violations);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableInput(ServerWebInputException ex) {
        log.debug("Unreadable request: {}", ex.getMessage());

        // A path/query parameter with the wrong type (e.g. a non-UUID id) vs. an unreadable body.
        return Optional.ofNullable(ex.getMethodParameter())
                .filter(parameter -> ex.getCause() instanceof TypeMismatchException)
                .map(parameter -> Optional.ofNullable(parameter.getParameterName())
                        .map("El parámetro '%s' tiene un formato inválido"::formatted)
                        .orElse("Un parámetro de la solicitud tiene un formato inválido"))
                .map(message -> build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, null))
                .orElseGet(() -> build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                        "El cuerpo de la solicitud es ilegible o tiene un formato inválido", null));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PatientNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return build(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", ex.getMessage(), null);
    }

    @ExceptionHandler(ConcurrentPatientUpdateException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ConcurrentPatientUpdateException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", ex.getMessage(), null);
    }


    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = Optional.ofNullable(ex.getReason()).orElse("Request error");
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, "HTTP_" + status, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred. Please try again.", null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                List<ErrorResponse.FieldViolation> violations) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), code, message, violations));
    }
}
