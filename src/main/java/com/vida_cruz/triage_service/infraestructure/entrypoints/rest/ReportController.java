package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.domain.api.GenerateOperationReport;
import com.vida_cruz.triage_service.domain.exceptions.InvalidReportRangeException;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.OperationReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/triage/reportes")
@RequiredArgsConstructor
public class ReportController {

    private final GenerateOperationReport generateOperationReport;

    @GetMapping("/operacion")
    public Mono<OperationReportResponse> operation(@RequestParam(name = "desde", required = false) String from,
                                                   @RequestParam(name = "hasta", required = false) String to) {
        return Mono.fromSupplier(() -> new ReportRange(parseDate("desde", from), parseDate("hasta", to)))
                .flatMap(generateOperationReport::generate)
                .map(OperationReportResponse::from);
    }

    private static Instant parseDate(String name, String value) {
        return Optional.ofNullable(value)
                .flatMap(ReportController::parseIso)
                .orElseThrow(() -> new InvalidReportRangeException(
                        "El parámetro '%s' es obligatorio y debe ser una fecha ISO 8601, por ejemplo 2026-11-13T00:00:00Z"
                                .formatted(name)));
    }

    private static Optional<Instant> parseIso(String text) {
        try {
            return Optional.of(OffsetDateTime.parse(text.strip()).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}